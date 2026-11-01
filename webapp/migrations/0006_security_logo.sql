-- A stock's logo, as PSE Edge shows it on the company's page. The file names
-- follow no pattern, so the cron reads each one off the page of a stock someone
-- holds (`resolveLogos`, `server/investments.ts`) and keeps the address; the
-- clients load the image from PSE Edge itself. NULL with none found.
-- `logo_checked_at` is when it last looked, found or not, so a stock is not
-- asked about again every hour.
ALTER TABLE securities ADD COLUMN logo_url TEXT CHECK (logo_url IS NULL OR logo_url GLOB 'https://*');
ALTER TABLE securities ADD COLUMN logo_checked_at TEXT;
