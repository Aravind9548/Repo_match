FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# Tectonic: a self-contained LaTeX engine for compiling tailored resumes to PDF.
ARG TECTONIC_VERSION=0.15.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && curl -fsSL "https://github.com/tectonic-typesetting/tectonic/releases/download/tectonic%40${TECTONIC_VERSION}/tectonic-${TECTONIC_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
       | tar -xz -C /usr/local/bin tectonic \
    && apt-get purge -y curl && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app ./app

RUN useradd --create-home appuser
USER appuser

# Download the TeX packages the resume template uses now, at build time, so the first PDF isn't a 100-second wait.
RUN python -c "import asyncio; from app.resume import Resume, ResumeSection, ResumeItem, render_latex, compile_pdf; \
r = Resume(name='Warm Up', contact=['a@b.co'], sections=[ResumeSection(title='Projects', items=[ResumeItem(title='x', subtitle='y', date='2026', bullets=['z'])], lines=['q'])]); \
res = asyncio.run(compile_pdf(render_latex(r), timeout_s=600)); print('tectonic warm-up ok:', res.ok); \
assert res.ok, res.log"

EXPOSE 8000
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
