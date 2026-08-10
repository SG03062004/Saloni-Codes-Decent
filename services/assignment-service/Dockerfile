FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir black ruff mypy

COPY app ./app
COPY mypy.ini .
COPY pyproject.toml .

EXPOSE 8085

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085"]