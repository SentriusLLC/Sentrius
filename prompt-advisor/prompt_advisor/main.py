"""Main FastAPI application for prompt_advisor service."""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
import nltk


from prompt_advisor.config import settings
from prompt_advisor.models import (
    ValidatePromptRequest,
    ValidatePromptResponse,
    CategoryRatings,
    CriteriaResponse,
    Criterion
)
from prompt_advisor.schema_loader import ATPlSchemaLoader
from prompt_advisor.llm_evaluator import LLMEvaluator
from prompt_advisor.scoring import ScoringEngine

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# Global instances
schema_loader: ATPlSchemaLoader = None
llm_evaluator: LLMEvaluator = None
scoring_engine: ScoringEngine = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup/shutdown events."""
    # Startup
    global schema_loader, llm_evaluator, scoring_engine
    
    logger.info("Initializing prompt_advisor service...")
    
    # Initialize schema loader
    schema_loader = ATPlSchemaLoader(settings.atpl_schema_url)
    logger.info(f"Schema loader initialized with URL: {settings.atpl_schema_url}")
    
    # Initialize LLM evaluator
    llm_evaluator = LLMEvaluator(
        endpoint=settings.llm_endpoint,
        api_key=settings.llm_api_key,
        model=settings.llm_model,
        enabled=settings.llm_enabled,
        custom_headers=settings.llm_custom_headers
    )
    logger.info(f"LLM evaluator initialized (enabled: {settings.llm_enabled})")
    
    # Initialize scoring engine
    scoring_engine = ScoringEngine(
        weight_purpose=settings.weight_purpose,
        weight_safety=settings.weight_safety,
        weight_compliance=settings.weight_compliance,
        weight_provenance=settings.weight_provenance,
        weight_autonomy=settings.weight_autonomy
    )
    logger.info(f"Scoring engine initialized with weights: {scoring_engine.get_weights()}")
    
    # Pre-load schema
    try:
        await schema_loader.load_schema()
        logger.info("ATPL schema pre-loaded successfully")
    except Exception as e:
        logger.warning(f"Failed to pre-load schema (will use default): {e}")
    
    logger.info("Service startup complete")
    
    yield
    
    # Shutdown
    logger.info("Shutting down prompt_advisor service...")

# --------------------------------------------------
# PRELOAD NLTK TOKENIZER AT STARTUP
# --------------------------------------------------
def preload_nltk_tokenizer():
    try:
        logger.info("Preloading NLTK corpora for TextBlob...")
        nltk.data.find("tokenizers/punkt")
        nltk.data.find("tokenizers/punkt_tab")
        nltk.data.find("taggers/averaged_perceptron_tagger")
        nltk.data.find("corpora/wordnet")
        nltk.data.find("corpora/omw-1.4")
        logger.info("NLTK corpora successfully preloaded.")
    except LookupError as e:
        logger.warning(f"NLTK resource missing: {e}")
        nltk.download("punkt")
        nltk.download("punkt_tab")
        nltk.download("averaged_perceptron_tagger")
        nltk.download("wordnet")
        nltk.download("omw-1.4")

# Call once at startup
preload_nltk_tokenizer()



# Create FastAPI app
app = FastAPI(
    title="Prompt Advisor",
    description="ATPL-based prompt validation service",
    version="0.1.0",
    lifespan=lifespan
)


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "prompt_advisor",
        "version": "0.1.0",
        "status": "running"
    }


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "healthy"}


@app.get("/criteria", response_model=CriteriaResponse)
async def get_criteria():
    """
    Get current ATPL criteria and their weights.
    
    Returns the list of evaluation criteria used for prompt validation.
    """
    global schema_loader, scoring_engine
    
    # Ensure instances are initialized (for testing without lifespan)
    if schema_loader is None:
        schema_loader = ATPlSchemaLoader(settings.atpl_schema_url)
    if scoring_engine is None:
        scoring_engine = ScoringEngine(
            weight_purpose=settings.weight_purpose,
            weight_safety=settings.weight_safety,
            weight_compliance=settings.weight_compliance,
            weight_provenance=settings.weight_provenance,
            weight_autonomy=settings.weight_autonomy
        )
    
    try:
        criteria_dict = await schema_loader.get_criteria()
        weights = scoring_engine.get_weights()
        
        criteria_list = []
        for key in ["purpose", "safety", "compliance", "provenance", "autonomy"]:
            description = criteria_dict.get(key, f"Evaluation of {key}")
            weight = weights.get(key, 0)
            criteria_list.append(
                Criterion(
                    name=key.capitalize(),
                    description=description,
                    weight=weight
                )
            )
        
        total_weight = sum(weights.values())
        
        return CriteriaResponse(
            criteria=criteria_list,
            total_weight=total_weight
        )
    except Exception as e:
        logger.error(f"Error retrieving criteria: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to retrieve criteria: {str(e)}")


@app.post("/validate_prompt", response_model=ValidatePromptResponse)
async def validate_prompt(request: ValidatePromptRequest):
    """
    Validate a prompt against ATPL criteria.
    
    Evaluates the prompt for compliance with ATPL categories including:
    - Purpose clarity
    - Safety and prohibited content
    - Data sensitivity and compliance
    - Trust and provenance
    - Agent autonomy bounds
    
    Returns a score (0-100), individual ratings, explanation, and recommendations.
    """
    global schema_loader, llm_evaluator, scoring_engine
    
    # Ensure instances are initialized (for testing without lifespan)
    if schema_loader is None:
        schema_loader = ATPlSchemaLoader(settings.atpl_schema_url)
    if llm_evaluator is None:
        llm_evaluator = LLMEvaluator(
            endpoint=settings.llm_endpoint,
            api_key=settings.llm_api_key,
            model=settings.llm_model,
            enabled=settings.llm_enabled
        )
    if scoring_engine is None:
        scoring_engine = ScoringEngine(
            weight_purpose=settings.weight_purpose,
            weight_safety=settings.weight_safety,
            weight_compliance=settings.weight_compliance,
            weight_provenance=settings.weight_provenance,
            weight_autonomy=settings.weight_autonomy
        )
    
    try:
        # Load schema (use custom URL if provided)
        if request.schema_url:
            custom_loader = ATPlSchemaLoader(request.schema_url)
            criteria = await custom_loader.get_criteria()
        else:
            criteria = await schema_loader.get_criteria()
        
        # Evaluate prompt using LLM
        evaluation = await llm_evaluator.evaluate_prompt(
            prompt=request.prompt,
            context=request.context,
            criteria=criteria
        )
        
        # Extract ratings
        ratings_dict = evaluation.get("ratings", {})
        
        # Ensure all required categories are present
        ratings = CategoryRatings(
            purpose=ratings_dict.get("purpose", 7),
            safety=ratings_dict.get("safety", 7),
            compliance=ratings_dict.get("compliance", 7),
            provenance=ratings_dict.get("provenance", 7),
            autonomy=ratings_dict.get("autonomy", 7)
        )
        
        # Calculate overall score
        score = scoring_engine.calculate_score(ratings_dict)
        
        # Build response
        response = ValidatePromptResponse(
            score=score,
            ratings=ratings,
            explanation=evaluation.get("explanation", "Prompt evaluated successfully."),
            recommendations=evaluation.get("recommendations", [])
        )
        
        logger.info(f"Prompt validated successfully - Score: {score}")
        return response
        
    except Exception as e:
        logger.error(f"Error validating prompt: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to validate prompt: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "prompt_advisor.main:app",
        host=settings.host,
        port=settings.port,
        reload=True
    )
