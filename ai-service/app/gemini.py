"""Gemini client construction — Vertex AI (ADC / service account) or an AI Studio API key."""

from dataclasses import dataclass

from google import genai
from google.genai import types
from google.oauth2 import service_account

from app.config import Settings

_CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"


@dataclass(frozen=True)
class GeminiClients:
    embed: genai.Client
    llm: genai.Client


def build_clients(s: Settings) -> GeminiClients:
    # Backoff of 2+4+8+16+30s ≈ 1 minute, long enough for a per-minute quota window to reset.
    http_options = types.HttpOptions(
        retry_options=types.HttpRetryOptions(attempts=6, initial_delay=2.0, max_delay=30.0)
    )
    if not s.google_genai_use_vertexai:
        client = genai.Client(api_key=s.gemini_api_key or None, http_options=http_options)
        return GeminiClients(embed=client, llm=client)

    project = s.google_cloud_project or None
    credentials = None
    if s.google_application_credentials:
        credentials = service_account.Credentials.from_service_account_file(
            s.google_application_credentials, scopes=[_CLOUD_PLATFORM_SCOPE]
        )
        project = project or credentials.project_id

    def vertex_client(location: str) -> genai.Client:
        return genai.Client(
            vertexai=True, project=project, location=location, credentials=credentials, http_options=http_options
        )

    embed = vertex_client(s.google_cloud_location)
    llm_location = s.llm_location or s.google_cloud_location
    llm = embed if llm_location == s.google_cloud_location else vertex_client(llm_location)
    return GeminiClients(embed=embed, llm=llm)
