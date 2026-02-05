FROM python:3.12-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    git \
    curl \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install uv
RUN curl -LsSf https://astral.sh/uv/install.sh | sh

# Environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/root/.local/bin:/root/.cargo/bin:$PATH" \
    UV_SYSTEM_PYTHON=1

# Copy dependency files
COPY pyproject.toml uv.lock ./
COPY README.md LICENSE ./

# Install dependencies
RUN uv sync --frozen --all-extras

# Copy source code
COPY . .

# Install package in development mode
RUN uv pip install -e .

# Default command for development
CMD ["/bin/bash"] 