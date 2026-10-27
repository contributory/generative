-- Frozen DDL from tracked app/schemas/org.starfall.generative.database.AppDatabase/9.json.
-- Only ${TABLE_NAME} substitutions have been applied; each statement occupies one line.
CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `messagesJson` TEXT NOT NULL, `tokenCount` INTEGER, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `profileId` TEXT, `summaryJson` TEXT, `reasoningEffort` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `chat_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT, `configJson` TEXT NOT NULL, `activeMcpJson` TEXT NOT NULL, `activeModelToolsJson` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `provider_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `icon` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `model_providers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `authJson` TEXT NOT NULL, `configJson` TEXT NOT NULL, `icon` TEXT, `sortOrder` INTEGER NOT NULL, `groupId` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `provider_models` (`id` TEXT NOT NULL, `modelsJson` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `mcp_servers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `protocol` TEXT NOT NULL, `url` TEXT, `headersJson` TEXT, `cachedToolsJson` TEXT, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `speech_services` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `provider` TEXT NOT NULL, `modelId` TEXT, `voice` TEXT NOT NULL, `speed` REAL NOT NULL, `pitch` REAL NOT NULL, `apiKey` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `optionsJson` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3b81f5d79afc80562e68b7b8c41fc3cc');
