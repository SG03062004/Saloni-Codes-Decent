from fastapi import FastAPI

app = FastAPI(title="assignment-service")


@app.get("/health")
def health():
    return {"status": "ok"}
