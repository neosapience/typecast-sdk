FROM python:3.14-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    git \
    curl \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install uv
RUN curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR=/usr/local/bin sh

# Environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
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

RUN useradd --create-home --shell /bin/bash app && chown -R app:app /app
USER app

# Default command for development
CMD ["/bin/bash"]
