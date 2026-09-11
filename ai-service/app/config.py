from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # Local dev reads the repo-root .env; in containers the variables come from the environment.
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), extra="ignore")

    google_genai_use_vertexai: bool = True
    google_cloud_project: str | None = None
    google_cloud_location: str = "us-central1"
    # Newer preview models are often only served from the "global" Vertex endpoint.
    llm_location: str | None = None
    gemini_api_key: str | None = None
    # Service-account key file; falls back to Application Default Credentials when unset.
    google_application_credentials: str | None = None

    llm_model: str = "gemini-3.1-pro-preview"
    embedding_model: str = "gemini-embedding-001"
    embedding_dim: int = 768
    llm_temperature: float = 0.2

    max_readme_chars_in_prompt: int = 5000


@lru_cache
def get_settings() -> Settings:
    return Settings()
