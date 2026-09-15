-- India coverage: India job lists on GitHub, more company boards, Workday career sites, and daily Google discovery.

INSERT INTO job_source (full_name, type, branch, path, default_kind) VALUES
    ('akshat0011/engineering-internship-india', 'GITHUB_LIST', 'main', 'README.md', 'INTERNSHIP'),
    ('FresherFlow/India-Jobs-Internships', 'GITHUB_LIST', 'main', 'README.md', 'NEW_GRAD'),
    ('Rohan-droid7341/internship-engine-india', 'GITHUB_LIST', 'main', 'README.md', 'INTERNSHIP');

-- Every board checked live for India roles before being added.
INSERT INTO job_source (full_name, type, slug, company_name, default_kind) VALUES
    ('ashby:tekion', 'ASHBY', 'tekion', 'Tekion', 'FULL_TIME'),
    ('ashby:atlan', 'ASHBY', 'atlan', 'Atlan', 'FULL_TIME'),
    ('greenhouse:glance', 'GREENHOUSE', 'glance', 'Glance', 'FULL_TIME'),
    ('greenhouse:zetaglobal', 'GREENHOUSE', 'zetaglobal', 'Zeta Global', 'FULL_TIME'),
    ('greenhouse:coursera', 'GREENHOUSE', 'coursera', 'Coursera', 'FULL_TIME'),
    ('greenhouse:toast', 'GREENHOUSE', 'toast', 'Toast', 'FULL_TIME'),
    ('greenhouse:observeai', 'GREENHOUSE', 'observeai', 'Observe.AI', 'FULL_TIME'),
    ('greenhouse:jumptrading', 'GREENHOUSE', 'jumptrading', 'Jump Trading', 'FULL_TIME'),
    ('smartrecruiters:ixigo', 'SMARTRECRUITERS', 'ixigo', 'ixigo', 'FULL_TIME'),
    ('smartrecruiters:sandisk', 'SMARTRECRUITERS', 'Sandisk', 'Sandisk', 'FULL_TIME'),
    ('smartrecruiters:westerndigital', 'SMARTRECRUITERS', 'WesternDigital', 'Western Digital', 'FULL_TIME'),
    ('smartrecruiters:continental', 'SMARTRECRUITERS', 'Continental', 'Continental', 'FULL_TIME'),
    ('smartrecruiters:grab', 'SMARTRECRUITERS', 'Grab', 'Grab', 'FULL_TIME'),
    ('smartrecruiters:unacademy', 'SMARTRECRUITERS', 'Unacademy', 'Unacademy', 'FULL_TIME');

-- Workday career sites, India roles only (slug = host|tenant|site).
INSERT INTO job_source (full_name, type, slug, company_name, default_kind) VALUES
    ('workday:nvidia', 'WORKDAY', 'nvidia.wd5.myworkdayjobs.com|nvidia|NVIDIAExternalCareerSite', 'NVIDIA', 'FULL_TIME'),
    ('workday:micron', 'WORKDAY', 'micron.wd1.myworkdayjobs.com|micron|External', 'Micron', 'FULL_TIME'),
    ('workday:hpe', 'WORKDAY', 'hpe.wd5.myworkdayjobs.com|hpe|Jobsathpe', 'Hewlett Packard Enterprise', 'FULL_TIME'),
    ('workday:salesforce', 'WORKDAY', 'salesforce.wd12.myworkdayjobs.com|salesforce|External_Career_Site', 'Salesforce', 'FULL_TIME'),
    ('workday:mastercard', 'WORKDAY', 'mastercard.wd1.myworkdayjobs.com|mastercard|CorporateCareers', 'Mastercard', 'FULL_TIME'),
    ('workday:citi', 'WORKDAY', 'citi.wd5.myworkdayjobs.com|citi|2', 'Citi', 'FULL_TIME'),
    ('workday:deutschebank', 'WORKDAY', 'db.wd3.myworkdayjobs.com|db|DBWebSite', 'Deutsche Bank', 'FULL_TIME'),
    ('workday:statestreet', 'WORKDAY', 'statestreet.wd1.myworkdayjobs.com|statestreet|Global', 'State Street', 'FULL_TIME'),
    ('workday:nxp', 'WORKDAY', 'nxp.wd3.myworkdayjobs.com|nxp|careers', 'NXP', 'FULL_TIME'),
    ('workday:cadence', 'WORKDAY', 'cadence.wd1.myworkdayjobs.com|cadence|External_Careers', 'Cadence', 'FULL_TIME'),
    ('workday:appliedmaterials', 'WORKDAY', 'wd1.myworkdaysite.com|amat|External', 'Applied Materials', 'FULL_TIME'),
    ('workday:adobe', 'WORKDAY', 'adobe.wd5.myworkdayjobs.com|adobe|external_experienced', 'Adobe', 'FULL_TIME'),
    ('workday:hp', 'WORKDAY', 'hp.wd5.myworkdayjobs.com|hp|ExternalCareerSite', 'HP', 'FULL_TIME'),
    ('workday:analogdevices', 'WORKDAY', 'analogdevices.wd1.myworkdayjobs.com|analogdevices|External', 'Analog Devices', 'FULL_TIME'),
    ('workday:intel', 'WORKDAY', 'intel.wd1.myworkdayjobs.com|intel|External', 'Intel', 'FULL_TIME'),
    ('workday:motorola', 'WORKDAY', 'motorolasolutions.wd5.myworkdayjobs.com|motorolasolutions|Careers', 'Motorola Solutions', 'FULL_TIME'),
    ('workday:blackrock', 'WORKDAY', 'blackrock.wd1.myworkdayjobs.com|blackrock|BlackRock_Professional', 'BlackRock', 'FULL_TIME'),
    ('workday:gartner', 'WORKDAY', 'gartner.wd5.myworkdayjobs.com|gartner|EXT', 'Gartner', 'FULL_TIME'),
    ('workday:autodesk', 'WORKDAY', 'autodesk.wd1.myworkdayjobs.com|autodesk|Ext', 'Autodesk', 'FULL_TIME'),
    ('workday:thomsonreuters', 'WORKDAY', 'thomsonreuters.wd5.myworkdayjobs.com|thomsonreuters|External_Career_Site', 'Thomson Reuters', 'FULL_TIME'),
    ('workday:barclays', 'WORKDAY', 'barclays.wd3.myworkdayjobs.com|barclays|External_Career_Site_Barclays', 'Barclays', 'FULL_TIME'),
    ('workday:nasdaq', 'WORKDAY', 'nasdaq.wd1.myworkdayjobs.com|nasdaq|Global_External_Site', 'Nasdaq', 'FULL_TIME'),
    ('workday:workday', 'WORKDAY', 'workday.wd5.myworkdayjobs.com|workday|Workday', 'Workday', 'FULL_TIME'),
    ('workday:paypal', 'WORKDAY', 'paypal.wd1.myworkdayjobs.com|paypal|jobs', 'PayPal', 'FULL_TIME'),
    ('workday:pwc', 'WORKDAY', 'pwc.wd3.myworkdayjobs.com|pwc|Global_Experienced_Careers', 'PwC', 'FULL_TIME'),
    ('workday:accenture', 'WORKDAY', 'accenture.wd103.myworkdayjobs.com|accenture|AccentureCareers', 'Accenture', 'FULL_TIME');

-- Postings found by the daily grounded Google searches. Not fetched like a board; filled by GoogleDiscoveryService.
INSERT INTO job_source (full_name, type, company_name, default_kind) VALUES
    ('google:daily-discovery', 'GOOGLE_SEARCH', 'Google search', 'FULL_TIME');
