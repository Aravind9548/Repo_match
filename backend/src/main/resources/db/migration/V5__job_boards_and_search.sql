-- Company job boards (Greenhouse, Lever, Ashby, SmartRecruiters) alongside the GitHub lists.
ALTER TABLE job_source ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'GITHUB_LIST';
ALTER TABLE job_source ADD COLUMN slug VARCHAR(120);
ALTER TABLE job_source ADD COLUMN company_name VARCHAR(200);
ALTER TABLE job_source ALTER COLUMN branch DROP NOT NULL;

-- Every board below was checked live before being added. Full-time boards default to FULL_TIME; a title
-- saying "intern" or "new grad" still overrides that per posting.
INSERT INTO job_source (full_name, type, slug, company_name, default_kind) VALUES
    ('greenhouse:okta', 'GREENHOUSE', 'okta', 'Okta', 'FULL_TIME'),
    ('greenhouse:databricks', 'GREENHOUSE', 'databricks', 'Databricks', 'FULL_TIME'),
    ('greenhouse:purestorage', 'GREENHOUSE', 'purestorage', 'Pure Storage', 'FULL_TIME'),
    ('greenhouse:mongodb', 'GREENHOUSE', 'mongodb', 'MongoDB', 'FULL_TIME'),
    ('greenhouse:zscaler', 'GREENHOUSE', 'zscaler', 'Zscaler', 'FULL_TIME'),
    ('greenhouse:rubrik', 'GREENHOUSE', 'rubrik', 'Rubrik', 'FULL_TIME'),
    ('greenhouse:gitlab', 'GREENHOUSE', 'gitlab', 'GitLab', 'FULL_TIME'),
    ('greenhouse:stripe', 'GREENHOUSE', 'stripe', 'Stripe', 'FULL_TIME'),
    ('greenhouse:inmobi', 'GREENHOUSE', 'inmobi', 'InMobi', 'FULL_TIME'),
    ('greenhouse:elastic', 'GREENHOUSE', 'elastic', 'Elastic', 'FULL_TIME'),
    ('greenhouse:hackerrank', 'GREENHOUSE', 'hackerrank', 'HackerRank', 'FULL_TIME'),
    ('greenhouse:druva', 'GREENHOUSE', 'druva', 'Druva', 'FULL_TIME'),
    ('greenhouse:twilio', 'GREENHOUSE', 'twilio', 'Twilio', 'FULL_TIME'),
    ('greenhouse:coinbase', 'GREENHOUSE', 'coinbase', 'Coinbase', 'FULL_TIME'),
    ('greenhouse:newrelic', 'GREENHOUSE', 'newrelic', 'New Relic', 'FULL_TIME'),
    ('greenhouse:samsara', 'GREENHOUSE', 'samsara', 'Samsara', 'FULL_TIME'),
    ('greenhouse:agoda', 'GREENHOUSE', 'agoda', 'Agoda', 'FULL_TIME'),
    ('greenhouse:airbnb', 'GREENHOUSE', 'airbnb', 'Airbnb', 'FULL_TIME'),
    ('greenhouse:datadog', 'GREENHOUSE', 'datadog', 'Datadog', 'FULL_TIME'),
    ('greenhouse:postman', 'GREENHOUSE', 'postman', 'Postman', 'FULL_TIME'),
    ('greenhouse:groww', 'GREENHOUSE', 'groww', 'Groww', 'FULL_TIME'),
    ('greenhouse:cloudflare', 'GREENHOUSE', 'cloudflare', 'Cloudflare', 'FULL_TIME'),
    ('greenhouse:figma', 'GREENHOUSE', 'figma', 'Figma', 'FULL_TIME'),
    ('greenhouse:pinterest', 'GREENHOUSE', 'pinterest', 'Pinterest', 'FULL_TIME'),
    ('greenhouse:reddit', 'GREENHOUSE', 'reddit', 'Reddit', 'FULL_TIME'),
    ('greenhouse:robinhood', 'GREENHOUSE', 'robinhood', 'Robinhood', 'FULL_TIME'),
    ('greenhouse:instacart', 'GREENHOUSE', 'instacart', 'Instacart', 'FULL_TIME'),
    ('lever:paytm', 'LEVER', 'paytm', 'Paytm', 'FULL_TIME'),
    ('lever:meesho', 'LEVER', 'meesho', 'Meesho', 'FULL_TIME'),
    ('lever:zeta', 'LEVER', 'zeta', 'Zeta', 'FULL_TIME'),
    ('lever:mindtickle', 'LEVER', 'mindtickle', 'Mindtickle', 'FULL_TIME'),
    ('lever:cred', 'LEVER', 'cred', 'CRED', 'FULL_TIME'),
    ('lever:fampay', 'LEVER', 'fampay', 'FamPay', 'FULL_TIME'),
    ('lever:palantir', 'LEVER', 'palantir', 'Palantir', 'FULL_TIME'),
    ('lever:spotify', 'LEVER', 'spotify', 'Spotify', 'FULL_TIME'),
    ('ashby:sarvam', 'ASHBY', 'sarvam', 'Sarvam AI', 'FULL_TIME'),
    ('ashby:openai', 'ASHBY', 'openai', 'OpenAI', 'FULL_TIME'),
    ('ashby:notion', 'ASHBY', 'notion', 'Notion', 'FULL_TIME'),
    ('ashby:composio', 'ASHBY', 'composio', 'Composio', 'FULL_TIME'),
    ('ashby:replit', 'ASHBY', 'replit', 'Replit', 'FULL_TIME'),
    ('ashby:perplexity', 'ASHBY', 'perplexity', 'Perplexity', 'FULL_TIME'),
    ('ashby:ramp', 'ASHBY', 'ramp', 'Ramp', 'FULL_TIME'),
    ('ashby:supabase', 'ASHBY', 'supabase', 'Supabase', 'FULL_TIME'),
    ('smartrecruiters:swiggy', 'SMARTRECRUITERS', 'Swiggy', 'Swiggy', 'FULL_TIME'),
    ('smartrecruiters:freshworks', 'SMARTRECRUITERS', 'Freshworks', 'Freshworks', 'FULL_TIME'),
    ('smartrecruiters:servicenow', 'SMARTRECRUITERS', 'ServiceNow', 'ServiceNow', 'FULL_TIME'),
    ('smartrecruiters:bosch', 'SMARTRECRUITERS', 'BoschGroup', 'Bosch', 'FULL_TIME');

-- Structured fields for search, filled by the next sync.
ALTER TABLE job_posting ADD COLUMN country VARCHAR(2);
ALTER TABLE job_posting ADD COLUMN remote BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_posting ADD COLUMN posted_at TIMESTAMPTZ;
-- Lowercased company, role, location and place aliases ("bangalore bengaluru india"), searched with word starts.
ALTER TABLE job_posting ADD COLUMN search_text TEXT NOT NULL DEFAULT '';

CREATE INDEX idx_job_posting_filter ON job_posting (closed, country, kind);
