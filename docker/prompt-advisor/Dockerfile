FROM python:3.11-bookworm

# Declare the argument for dev certs
ARG INCLUDE_DEV_CERTS=false

# Set environment so you can use in RUN
ENV INCLUDE_DEV_CERTS=${INCLUDE_DEV_CERTS}

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libblas-dev liblapack-dev gfortran build-essential ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Copy dev certs for optional import
COPY dev-certs/sentrius-ca.crt /tmp/sentrius-ca.crt

# Import dev CA cert into system trust store if enabled
RUN if [ "$INCLUDE_DEV_CERTS" = "true" ] && [ -f /tmp/sentrius-ca.crt ]; then \
      echo "Importing dev CA cert for Python..." && \
      cp /tmp/sentrius-ca.crt /usr/local/share/ca-certificates/sentrius-ca.crt && \
      update-ca-certificates ; \
    else \
      echo "Skipping cert import"; \
    fi

# Copy requirements from submodule
COPY prompt-advisor/requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download NLTK data
ENV NLTK_DATA=/usr/local/share/nltk_data
RUN mkdir -p $NLTK_DATA && \
    python -m textblob.download_corpora lite && \
    python -m nltk.downloader -d $NLTK_DATA punkt punkt_tab averaged_perceptron_tagger wordnet omw-1.4

# Copy code from submodule
COPY prompt-advisor/prompt_advisor ./prompt_advisor

# Create non-root user
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app /usr/local/share/nltk_data
USER appuser

EXPOSE 8000

ENV HOST=0.0.0.0
ENV PORT=8000
ENV ATPL_SCHEMA_URL=https://raw.githubusercontent.com/SentriusLLC/atpl/main/atpl.schema.json
ENV LLM_ENDPOINT=""
ENV LLM_API_KEY=""
ENV LLM_MODEL=gpt-4
ENV LLM_ENABLED=false
# Keycloak configuration for native token management
ENV KEYCLOAK_URL=""
ENV KEYCLOAK_REALM=sentrius
ENV KEYCLOAK_CLIENT_ID=prompt-advisor
ENV KEYCLOAK_CLIENT_SECRET=""
ENV KEYCLOAK_VERIFY_SSL=true
# Scoring weights
ENV WEIGHT_PURPOSE=15
ENV WEIGHT_SAFETY=30
ENV WEIGHT_COMPLIANCE=25
ENV WEIGHT_PROVENANCE=15
ENV WEIGHT_AUTONOMY=15
ENV SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt

CMD ["python", "-m", "uvicorn", "prompt_advisor.main:app", "--host", "0.0.0.0", "--port", "8000"]
