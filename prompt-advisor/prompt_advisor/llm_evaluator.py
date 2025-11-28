"""LLM evaluator module for semantic + linguistic prompt analysis with trust scoring."""
import httpx
from typing import Dict, Any, Optional
import logging
import json
import re
from textblob import TextBlob

logger = logging.getLogger(__name__)


class LLMEvaluator:
    """Evaluator that uses both rule-based NLP and an LLM to assess prompt trustworthiness."""

    def __init__(self, endpoint: str, api_key: str = "", model: str = "gpt-4",
                 enabled: bool = False, custom_headers: str = ""):
        self.endpoint = endpoint
        self.api_key = api_key
        self.model = model
        self.enabled = enabled
        self.custom_headers = self._parse_custom_headers(custom_headers)

    # ================================================================
    # Core Public Entry Point
    # ================================================================
    async def evaluate_prompt(
        self,
        prompt: str,
        context: Optional[Dict[str, Any]] = None,
        criteria: Optional[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """Evaluate a prompt using both rule-based analysis and LLM feedback."""
        try:
            # Step 1: always run rule-based linguistic analysis
            rules_scores = self._rule_based_text_quality(prompt)

            # Step 2: get LLM analysis if enabled
            if not self.enabled or not self.endpoint:
                base_eval = self._get_neutral_evaluation()
                base_eval["ratings"].update(rules_scores)
                merged = self._merge_scores(rules_scores, base_eval)
            else:
                evaluation_prompt = self._build_evaluation_prompt(prompt, context, criteria)
                result = await self._call_llm(evaluation_prompt)
                llm_eval = self._parse_llm_response(result)
                merged = self._merge_scores(rules_scores, llm_eval)

            # Step 3: compute the overall trust score
            merged["trust_score"] = self._compute_trust_score(merged["ratings"])
            merged["explanation"] += f"\nComposite Trust Score: {merged['trust_score']} / 100."
            return merged

        except Exception as e:
            logger.error(f"LLM evaluation failed: {e}")
            base_eval = self._get_neutral_evaluation()
            base_eval["ratings"].update(self._rule_based_text_quality(prompt))
            base_eval["trust_score"] = self._compute_trust_score(base_eval["ratings"])
            return base_eval

    # ================================================================
    # Linguistic Pre-Analysis Layer
    # ================================================================
    def _rule_based_text_quality(self, prompt: str) -> Dict[str, int]:
        """Basic orthographic and structural checks using TextBlob and regex."""
        blob = TextBlob(prompt)
        words = prompt.split()
        num_words = len(words) or 1

        # Orthography: spelling corrections difference
        corrected_words = blob.correct().split()
        spelling_diff = sum(1 for w1, w2 in zip(words, corrected_words) if w1.lower() != w2.lower())
        orthography_score = max(10 - spelling_diff, 0)

        # Readability: penalize long sentences (>25 words)
        long_sentences = sum(1 for s in blob.sentences if len(s.words) > 25)
        readability_score = max(10 - long_sentences * 2, 0)

        # Graphemics: non-ASCII, weird symbols, inconsistent spacing
        non_ascii_chars = len(re.findall(r"[^\x00-\x7F]", prompt))
        excessive_symbols = len(re.findall(r"[^a-zA-Z0-9\s,.!?;:'\"()-]", prompt))
        graphemics_penalty = min(non_ascii_chars + excessive_symbols, 10)
        graphemics_score = max(10 - graphemics_penalty, 0)

        # Structural clarity: punctuation ratio
        punctuations = len(re.findall(r"[,.!?;:]", prompt))
        clarity_ratio = punctuations / num_words
        semantic_clarity_score = 10 if 0.01 < clarity_ratio < 0.15 else max(8 - abs(0.08 - clarity_ratio) * 100, 0)

        return {
            "orthography": round(orthography_score),
            "graphemics": round(graphemics_score),
            "readability": round(readability_score),
            "semantic_clarity": round(semantic_clarity_score)
        }

    # ================================================================
    # Helper / Utility Methods
    # ================================================================
    def _parse_custom_headers(self, custom_headers_str: str) -> Dict[str, str]:
        headers = {}
        if not custom_headers_str:
            return headers
        for pair in custom_headers_str.split(","):
            pair = pair.strip()
            if ":" in pair:
                key, value = pair.split(":", 1)
                headers[key.strip()] = value.strip()
        return headers

    def _build_evaluation_prompt(
        self,
        prompt: str,
        context: Optional[Dict[str, Any]],
        criteria: Optional[Dict[str, str]]
    ) -> str:
        """Build the evaluation prompt for the LLM."""
        criteria_text = ""
        if criteria:
            criteria_text = "\n".join([f"- {key}: {desc}" for key, desc in criteria.items()])

        context_text = ""
        if context:
            context_text = f"\n\nContext: {json.dumps(context, indent=2)}"

        return f"""You are an AI evaluator specializing in trust, compliance, and linguistic clarity.
Evaluate the following prompt across all provided dimensions.

Criteria:
{criteria_text}

Rate each from 0–10 where:
0–3 = Poor/Non-compliant
4–6 = Acceptable with concerns
7–10 = Good/Compliant

Include both semantic (meaning) and linguistic (form) aspects.

Prompt:
{prompt}{context_text}

Respond strictly in JSON:
{{
  "ratings": {{
    "purpose": <0-10>,
    "safety": <0-10>,
    "compliance": <0-10>,
    "provenance": <0-10>,
    "autonomy": <0-10>,
    "pragmatic_tone": <0-10>,
    "lexical_precision": <0-10>
  }},
  "explanation": "<detailed explanation>",
  "recommendations": ["<recommendation 1>", "<recommendation 2>", ...]
}}"""

    async def _call_llm(self, evaluation_prompt: str) -> str:
        headers = dict(self.custom_headers)
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        payload = {
            "model": self.model,
            "messages": [
                {"role": "system",
                 "content": "You are a helpful AI assistant that evaluates prompts for trust, compliance, and linguistic quality."},
                {"role": "user", "content": evaluation_prompt}
            ],
            "temperature": 0.3
        }

        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(self.endpoint, headers=headers, json=payload)
            response.raise_for_status()
            result = response.json()

            if "choices" in result and result["choices"]:
                return result["choices"][0]["message"]["content"]
            elif "content" in result:
                return result["content"]
            else:
                return json.dumps(result)

    def _parse_llm_response(self, response: str) -> Dict[str, Any]:
        try:
            if "```json" in response:
                response = response.split("```json")[1].split("```")[0].strip()
            elif "```" in response:
                response = response.split("```")[1].split("```")[0].strip()

            result = json.loads(response)
            if "ratings" not in result:
                return self._get_neutral_evaluation()
            return {
                "ratings": result.get("ratings", {}),
                "explanation": result.get("explanation", "LLM evaluation completed."),
                "recommendations": result.get("recommendations", [])
            }
        except Exception as e:
            logger.error(f"Failed to parse LLM response: {e}\nRaw: {response}")
            return self._get_neutral_evaluation()

    def _merge_scores(self, rules: Dict[str, int], llm_eval: Dict[str, Any]) -> Dict[str, Any]:
        """Average overlapping keys; combine all categories."""
        merged = llm_eval["ratings"].copy()
        for k, v in rules.items():
            merged[k] = round((merged.get(k, 7) + v) / 2)
        return {
            **llm_eval,
            "ratings": merged,
            "explanation": llm_eval.get("explanation", "") + "\nSurface-layer linguistic analysis included."
        }

    def _get_neutral_evaluation(self) -> Dict[str, Any]:
        return {
            "ratings": {
                "purpose": 7, "safety": 7, "compliance": 7, "provenance": 7, "autonomy": 7
            },
            "explanation": "Automated evaluation completed. LLM evaluation unavailable.",
            "recommendations": [
                "Review prompt for clarity and compliance.",
                "Ensure no sensitive or ambiguous data included."
            ]
        }
    
    # ================================================================
    # Adaptive Trust Scoring
    # ================================================================
    def _get_weight_profile(self, context: Optional[Dict[str, Any]]) -> Dict[str, float]:
        """
        Determine weighting based on context (environment, agent type, sensitivity).
        """
        # Default balanced weighting
        weights = {
            "purpose": 0.1,
            "safety": 0.2,
            "compliance": 0.2,
            "provenance": 0.1,
            "autonomy": 0.1,
            "orthography": 0.05,
            "graphemics": 0.05,
            "readability": 0.05,
            "semantic_clarity": 0.05,
            "pragmatic_tone": 0.05,
            "lexical_precision": 0.05
        }

        if not context:
            return weights

        env = context.get("environment", "").lower()
        agent_type = context.get("agent_type", "").lower()
        sensitivity = context.get("data_sensitivity", "medium").lower()

        # In production environments: emphasize safety and compliance
        if env in ["prod", "production"]:
            weights["safety"] += 0.1
            weights["compliance"] += 0.1
            weights["autonomy"] -= 0.05
            weights["readability"] -= 0.05

        # In dev/test: emphasize clarity and purpose
        elif env in ["dev", "development", "test", "sandbox"]:
            weights["purpose"] += 0.1
            weights["semantic_clarity"] += 0.05
            weights["readability"] += 0.05
            weights["safety"] -= 0.05

        # Sensitive data domains (health, finance, gov)
        if sensitivity in ["high", "critical"]:
            weights["compliance"] += 0.15
            weights["provenance"] += 0.1
            weights["orthography"] -= 0.05  # lower importance of surface
            weights["readability"] -= 0.05

        # Conversational agents: tone & lexical precision matter
        if "chat" in agent_type or "assistant" in agent_type:
            weights["pragmatic_tone"] += 0.05
            weights["lexical_precision"] += 0.05
            weights["safety"] -= 0.05

        # Normalize to sum to 1.0
        total = sum(weights.values())
        for k in weights:
            weights[k] /= total

        return weights

    def _compute_trust_score(self, ratings: Dict[str, int], context: Optional[Dict[str, Any]] = None) -> int:
        """Compute composite trust score with adaptive weighting."""
        weights = self._get_weight_profile(context)
        weighted_sum = 0.0
        for k, v in ratings.items():
            w = weights.get(k, 0.05)
            weighted_sum += (v / 10.0) * w
        return round(weighted_sum * 100)