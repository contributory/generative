-- Frozen released v1 DDL. v1 predates speech_services and the later sort/group fields.
CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `messagesJson` TEXT NOT NULL, `tokenCount` INTEGER, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `profileId` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `chat_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT, `configJson` TEXT NOT NULL, `activeMcpJson` TEXT NOT NULL, `activeModelToolsJson` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `llm_providers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `authJson` TEXT NOT NULL, `configJson` TEXT NOT NULL, `icon` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `llm_models` (`id` TEXT NOT NULL, `modelsJson` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `mcp_servers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `protocol` TEXT NOT NULL, `url` TEXT, `headersJson` TEXT, PRIMARY KEY(`id`));
