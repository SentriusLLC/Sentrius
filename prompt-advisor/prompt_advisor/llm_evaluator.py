"""LLM evaluator module for semantic + linguistic prompt analysis with trust scoring."""
import httpx
from typing import Dict, Any, Optional
import logging
import json
import re
import uuid
import time
from textblob import TextBlob
from keycloak import KeycloakOpenID

logger = logging.getLogger(__name__)


class LLMEvaluator:
    """Evaluator that uses both rule-based NLP and an LLM to assess prompt trustworthiness."""

    def __init__(self, endpoint: str, api_key: str = "", model: str = "gpt-4",
                 enabled: bool = False, custom_headers: str = "",
                 keycloak_url: str = "", keycloak_realm: str = "sentrius",
                 keycloak_client_id: str = "", keycloak_client_secret: str = "",
                 keycloak_verify_ssl: bool = True):
        self.endpoint = endpoint
        self.api_key = api_key
        self.model = model
        self.enabled = enabled
        self.custom_headers = self._parse_custom_headers(custom_headers)
        
        # Keycloak configuration for native token management
        self.keycloak_url = keycloak_url
        self.keycloak_realm = keycloak_realm
        self.keycloak_client_id = keycloak_client_id
        self.keycloak_client_secret = keycloak_client_secret
        self.keycloak_verify_ssl = keycloak_verify_ssl
        
        # Token caching
        self._cached_token: Optional[str] = None
        self._token_expires_at: float = 0
        
        # Initialize Keycloak client if configured
        self._keycloak_client: Optional[KeycloakOpenID] = None
        if keycloak_url and keycloak_client_id and keycloak_client_secret:
            try:
                self._keycloak_client = KeycloakOpenID(
                    server_url=keycloak_url,
                    client_id=keycloak_client_id,
                    realm_name=keycloak_realm,
                    client_secret_key=keycloak_client_secret,
                    verify=keycloak_verify_ssl
                )
                logger.info(f"Keycloak client initialized for realm '{keycloak_realm}'")
            except Exception as e:
                logger.error(f"Failed to initialize Keycloak client: {e}")

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
            merged["explanation"] = (
                merged.get("explanation", "")
                + f"\nComposite Trust Score: {merged['trust_score']} / 100."
            )

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
        Deliberately consider each dimension independently before assigning scores.

        Definition notes:
        - **Purpose** refers to how clearly and explicitly the agent’s intended function is defined. Be most critical of
        ambiguity of operations. Minimize ambiguity.
        - **Safety** refers to the potential for misuse, harm, escalation, or unintended consequences.
        - **Compliance** refers to whether the agent’s implied actions, data access, and decision-making would reasonably
        align with privacy expectations, regulatory principles, and organizational policy constraints if executed. Assume
        well known compliance regulations based on intent of the agent.
        - **Provenance** refers to the clarity and traceability of which agent acts, what data is used or produced, and whether actions can be attributed and audited.
        - **Autonomy** refers to the degree of independent action implied without explicit oversight or approval.


        Criteria:
        {criteria_text}

        Scoring scale (0–10):
        - **0–3**: Poor / non-compliant / high risk
        - **4–7**: Acceptable with concerns or ambiguity
        - **8–10**: Clear, well-defined, and low-risk

        Include both:
        - **Semantic reasoning** (what the prompt means and enables)
        - **Linguistic quality** (clarity, precision, tone)

        Prompt to evaluate:
        {prompt}{context_text}

        Output requirements:
        - Respond with a single valid JSON object only.
        - Do not include markdown, comments, or extra text.

        Required JSON format:
        {{
          "ratings": {{
            "purpose": <integer 0-10>,
            "safety": <integer 0-10>,
            "compliance": <integer 0-10>,
            "provenance": <integer 0-10>,
            "autonomy": <integer 0-10>,
            "pragmatic_tone": <integer 0-10>,
            "lexical_precision": <integer 0-10>
          }},
          "explanation": "<concise but specific justification covering major risks and strengths>",
          "recommendations": ["<actionable recommendation 1>", "<actionable recommendation 2>", "..."]
        }}"""

    def _get_keycloak_token(self) -> Optional[str]:
        """Get Keycloak token using native library, with caching."""
        if not self._keycloak_client:
            return None
            
        # Check if cached token is still valid (with 30s buffer)
        if self._cached_token and time.time() < (self._token_expires_at - 30):
            logger.debug("Using cached Keycloak token")
            return self._cached_token
            
        try:
            # Get new token using client credentials grant
            token_response = self._keycloak_client.token(grant_type="client_credentials")
            access_token = token_response.get("access_token")
            expires_in = token_response.get("expires_in", 300)
            
            if access_token:
                self._cached_token = access_token
                self._token_expires_at = time.time() + expires_in
                logger.info(f"Obtained new Keycloak token (expires in {expires_in}s)")
                return access_token
        except Exception as e:
            logger.error(f"Failed to obtain Keycloak token: {e}")
            
        return None

    async def _call_llm(self, evaluation_prompt: str) -> str:
        headers = dict(self.custom_headers)
        headers["X-Communication-Id"] = str(uuid.uuid4())
        
        # Get token from Keycloak using native library
        logger.debug("hey-oh")
        token = self._get_keycloak_token()
        if token:
            headers["Authorization"] = f"Bearer {token}"
            logger.debug("Using Keycloak token for LLM request")
        elif "Authorization" not in headers:
            # Fall back to api_key if Keycloak not configured
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

            logger.info(result)

            return self._extract_response_text(result)

    def _extract_response_text(self, result: dict) -> str:
        # Responses API
        if "output" in result and result["output"]:
            texts = []
            for item in result["output"]:
                if item.get("type") == "message":
                    for content in item.get("content", []):
                        if content.get("type") == "output_text":
                            texts.append(content.get("text", ""))
            if texts:
                return "\n".join(texts)

        # Legacy chat.completions fallback
        if "choices" in result and result["choices"]:
            return result["choices"][0]["message"]["content"]

        # Last resort
        return json.dumps(result)

    def _parse_llm_response(self, response: str) -> Dict[str, Any]:
        try:
            response = response.strip()

            logger.info(response)

            # Strip markdown if present
            if response.startswith("```"):
                response = response.split("```")[1]

            parsed = json.loads(response)

            if not isinstance(parsed, dict) or "ratings" not in parsed:
                raise ValueError("Invalid LLM schema")

            return {
                "ratings": parsed.get("ratings", {}),
                "explanation": parsed.get("explanation", ""),
                "recommendations": parsed.get("recommendations", [])
            }

        except Exception as e:
            logger.error(f"Failed to parse LLM response: {e}")
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

    # ================================================================
    # Prompt Refinement
    # ================================================================
    async def refine_prompt(
        self,
        prompt: str,
        recommendations: list,
        explanation: str = "",
        context: Optional[Dict[str, Any]] = None
    ) -> str:
        """Use the LLM to refine a prompt based on recommendations."""
        if not self.enabled or not self.endpoint:
            logger.warning("LLM not enabled, returning original prompt")
            return prompt

        try:
            refinement_prompt = self._build_refinement_prompt(prompt, recommendations, explanation, context)
            logger.info(f"Refinement prompt{refinement_prompt}")
            result = await self._call_llm(refinement_prompt)
            refined = self._parse_refinement_response(result)
            return refined if refined else prompt
        except Exception as e:
            logger.error(f"Prompt refinement failed: {e}")
            return prompt

    def _build_refinement_prompt(
        self,
        prompt: str,
        recommendations: list,
        explanation: str,
        context: Optional[Dict[str, Any]]
    ) -> str:
        """Build the refinement prompt for the LLM."""
        recommendations_text = "\n".join([f"- {rec}" for rec in recommendations]) if recommendations else "No specific recommendations."
        
        context_text = ""
        if context:
            context_text = f"\n\nContext: {json.dumps(context, indent=2)}"

        return f"""You are an AI prompt engineer specializing in improving prompts for trust, safety, and clarity.

You have been given a prompt that was evaluated and received the following feedback:

**Original Prompt:**
{prompt}

**Evaluation Explanation:**
{explanation}

**Recommendations for Improvement:**
{recommendations_text}
{context_text}

Your task is to rewrite the prompt to address the recommendations while maintaining the original intent.

Guidelines:
1. Improve clarity and specificity of purpose
2. Address any safety or compliance concerns
3. Add appropriate constraints and boundaries
4. Improve provenance and auditability where applicable
5. Clarify autonomy bounds if needed
6. Maintain the core functionality and intent of the original prompt

Output requirements:
- Respond with ONLY the refined prompt text
- Do not include any explanations, markdown formatting, or additional commentary
- The refined prompt should be ready to use as-is

Refined prompt:"""

    def _parse_refinement_response(self, response: str) -> Optional[str]:
        """Parse the LLM refinement response to extract the refined prompt."""
        try:
            # Clean up the response
            refined = response.strip()
            
            # Remove any markdown code blocks if present
            if refined.startswith("```"):
                lines = refined.split("\n")
                # Find and remove both opening and closing backticks
                start_idx = 1  # Skip opening ```
                end_idx = len(lines)
                for i in range(len(lines) - 1, 0, -1):
                    if lines[i].strip() == "```":
                        end_idx = i
                        break
                refined = "\n".join(lines[start_idx:end_idx])
            
            # Remove common prefixes the LLM might add
            prefixes_to_remove = [
                "Refined prompt:",
                "Here is the refined prompt:",
                "Here's the refined prompt:",
                "The refined prompt is:",
            ]
            for prefix in prefixes_to_remove:
                if refined.lower().startswith(prefix.lower()):
                    refined = refined[len(prefix):].strip()
            
            return refined if refined else None
        except Exception as e:
            logger.error(f"Failed to parse refinement response: {e}")
            return None