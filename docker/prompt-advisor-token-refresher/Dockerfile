FROM python:3.11-slim

# Declare the argument for dev certs
ARG INCLUDE_DEV_CERTS=false

# Set environment so you can use in RUN
ENV INCLUDE_DEV_CERTS=${INCLUDE_DEV_CERTS}

WORKDIR /app

# Install ca-certificates for SSL trust store management
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Copy dev cert for optional import
COPY dev-certs/sentrius-ca.crt /tmp/sentrius-ca.crt

# Import dev CA cert into system trust store if enabled
RUN if [ "$INCLUDE_DEV_CERTS" = "true" ] && [ -f /tmp/sentrius-ca.crt ]; then \
      echo "Importing dev CA cert for Python..." && \
      cp /tmp/sentrius-ca.crt /usr/local/share/ca-certificates/sentrius-ca.crt && \
      update-ca-certificates ; \
    else \
      echo "Skipping cert import"; \
    fi

# Install requests library
RUN pip install --no-cache-dir requests==2.31.0

# Copy token refresher script
COPY token_refresher.py /app/token_refresher.py

# Make script executable
RUN chmod +x /app/token_refresher.py

# Create non-root user
RUN useradd -m -u 1000 tokenrefresher
USER tokenrefresher

ENTRYPOINT ["python", "/app/token_refresher.py"]
