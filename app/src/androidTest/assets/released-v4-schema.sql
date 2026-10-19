-- Frozen DDL from the released v4 Room schema.
CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `messagesJson` TEXT NOT NULL, `tokenCount` INTEGER, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `profileId` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `chat_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT, `configJson` TEXT NOT NULL, `activeMcpJson` TEXT NOT NULL, `activeModelToolsJson` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `llm_providers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `authJson` TEXT NOT NULL, `configJson` TEXT NOT NULL, `icon` TEXT, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `llm_models` (`id` TEXT NOT NULL, `modelsJson` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `mcp_servers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `protocol` TEXT NOT NULL, `url` TEXT, `headersJson` TEXT, `cachedToolsJson` TEXT, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `speech_services` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `provider` TEXT NOT NULL, `modelId` TEXT, `voice` TEXT NOT NULL, `speed` REAL NOT NULL, `pitch` REAL NOT NULL, `apiKey` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e2146e61b8b4aaac530b21f1689652e0');
