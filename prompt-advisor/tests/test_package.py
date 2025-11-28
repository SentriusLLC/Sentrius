"""Test package structure and metadata."""
import prompt_validator


def test_version():
    """Test that version is defined and is a string."""
    assert hasattr(prompt_validator, "__version__")
    assert isinstance(prompt_validator.__version__, str)
    assert len(prompt_validator.__version__) > 0


def test_exports():
    """Test that all expected symbols are exported."""
    expected_exports = [
        "__version__",
        "settings",
        "Settings",
        "ValidatePromptRequest",
        "ValidatePromptResponse",
        "CategoryRatings",
        "CriteriaResponse",
        "Criterion",
        "ATPlSchemaLoader",
        "LLMEvaluator",
        "ScoringEngine",
    ]
    
    for export in expected_exports:
        assert hasattr(prompt_validator, export), f"Missing export: {export}"
    
    assert hasattr(prompt_validator, "__all__")
    assert set(prompt_validator.__all__) == set(expected_exports)


def test_module_imports():
    """Test that all exported components can be imported."""
    from prompt_validator import (
        __version__,
        settings,
        Settings,
        ValidatePromptRequest,
        ValidatePromptResponse,
        CategoryRatings,
        CriteriaResponse,
        Criterion,
        ATPlSchemaLoader,
        LLMEvaluator,
        ScoringEngine,
    )
    
    # Basic smoke tests
    assert isinstance(__version__, str)
    assert hasattr(settings, "host")
    
    # Test Settings class can be instantiated
    test_settings = Settings()
    assert hasattr(test_settings, "host")
    
    # Test that models can be instantiated
    request = ValidatePromptRequest(prompt="test")
    assert request.prompt == "test"
    
    ratings = CategoryRatings(
        purpose=7,
        safety=8,
        compliance=7,
        provenance=7,
        autonomy=7
    )
    assert ratings.purpose == 7


def test_fastapi_app_exists():
    """Test that the FastAPI app can be imported."""
    from prompt_validator.main import app
    assert app is not None
    assert hasattr(app, "routes")
