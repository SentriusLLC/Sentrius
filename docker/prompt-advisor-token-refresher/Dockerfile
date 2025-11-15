FROM python:3.11-slim

WORKDIR /app

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
