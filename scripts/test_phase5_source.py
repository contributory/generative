import unittest

from phase5_source import audit_sources

BASE = 'app/src/main/kotlin/org/starfall/multigateway/shared/storage/'


def schema(*columns):
    return {'database': {'version': 1, 'entities': [
        {'tableName': 'conversations', 'fields': [{'columnName': column} for column in columns]}]}}


def failures(reports):
    return [message for status, message in reports if status == 'FAIL']


class SourceGuardTest(unittest.TestCase):
    def test_blob_column_is_rejected(self):
        self.assertTrue(any('messagesJson' in message for message in failures(audit_sources({}, schema('id', 'messagesJson')))))

    def test_quarantine_json_is_explicitly_justified(self):
        value = schema('rawJson')
        value['database']['entities'][0]['tableName'] = 'conversation_migration_quarantine'
        self.assertEqual([], failures(audit_sources({}, value)))

    def test_unencrypted_repository_serialization_is_rejected(self):
        source = {BASE + 'ProfileRepository.kt': 'val config = json.encodeToString(profile)'}
        self.assertTrue(any('unclassified' in message for message in failures(audit_sources(source, schema('id')))))

    def test_encrypted_payload_requires_review_not_blanket_pass(self):
        source = {BASE + 'Mapper.kt': 'val value = SecretCipher.encrypt(json.encodeToString(payload))'}
        reports = audit_sources(source, schema('id'))
        self.assertEqual([], failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'encrypted serialization' in message for status, message in reports))

    def test_nearby_encryption_does_not_excuse_plaintext(self):
        source = {BASE + 'Mapper.kt': 'val key = SecretCipher.encrypt(secret)\nval config = json.encodeToString(config)'}
        self.assertTrue(any('unclassified' in message for message in failures(audit_sources(source, schema('id')))))

    def test_preferences_have_separate_boundary(self):
        reports = audit_sources({BASE + 'preferences/Store.kt': 'val value = json.encodeToString(settings)'}, schema('id'))
        self.assertEqual([], failures(reports))
        self.assertTrue(any('preference boundary' in message for _, message in reports))

    def test_comments_and_strings_are_not_serializer_calls(self):
        source = {BASE + 'Mapper.kt': '// json.encodeToString(thing)\nval text = "json.encodeToString(thing)"'}
        self.assertEqual([], failures(audit_sources(source, schema('id'))))

    def test_list_cannot_decrypt_through_local_mapper(self):
        source = {BASE + 'McpRepository.kt': '''class McpRepository {
    private val stored = dao.getAllServers().map { it.map { entityToModel(it) } }
    private fun entityToModel(entity: Entity) = decode(entity)
    private fun decode(entity: Entity) = SecretCipher.decryptStrict(entity.payload)
}'''}
        self.assertTrue(any('list mapping reaches' in message for message in failures(audit_sources(source, schema('id')))))

    def test_list_cannot_decrypt_through_external_mapper(self):
        source = {
            BASE + 'ModelRepository.kt': '''class ModelRepository {
    private val mapper = ProviderStorageMapper(db)
    private val storedProviders = dao.getAllProviders().map { mapper.metadata(it) }
}''',
            BASE + 'ProviderStorageMapper.kt': '''class ProviderStorageMapper {
    suspend fun metadata(entity: Entity) = selected(entity)
    suspend fun selected(entity: Entity) = dao.getAuth(entity.authId)
}'''}
        self.assertTrue(any('list mapping reaches' in message for message in failures(audit_sources(source, schema('id')))))

    def test_safe_metadata_shape_is_still_not_runtime_proof(self):
        source = {BASE + 'McpRepository.kt': '''class McpRepository {
    private val stored = dao.getAllServers().map { it.map { metadata(it) } }
    private fun metadata(entity: Entity) = McpInfo(entity.name)
    private fun selected(entity: Entity) = dao.getAuth(entity.authId)
}'''}
        reports = audit_sources(source, schema('id'))
        self.assertEqual([], failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'instrumented repository tests' in message for status, message in reports))

    def test_unresolved_mapper_is_reviewed(self):
        source = {BASE + 'ModelRepository.kt': '''class ModelRepository {
    private val storedProviders = dao.getAllProviders().map { mapper.metadata(it) }
}'''}
        self.assertTrue(any('unresolved metadata' in message for _, message in audit_sources(source, schema('id'))))

    def test_full_history_rewrite_and_unsafe_rename_are_rejected(self):
        source = {BASE + 'ConversationRepository.kt': '''class ConversationRepository {
    suspend fun saveConversation(chat: Conversation) { dao.deleteMessages(chat.id) }
    suspend fun renameConversation(id: String, title: String) { dao.insertOrUpdate(entity) }
}'''}
        errors = failures(audit_sources(source, schema('id')))
        self.assertTrue(any('entire message history' in message for message in errors))
        self.assertTrue(any('cascading history loss' in message for message in errors))

    def test_missing_migration_registration_is_rejected(self):
        value = schema('id')
        value['database']['version'] = 3
        source = {BASE + 'database/AppDatabase.kt': '''class Database {
    val MIGRATION_1_2 = Migration(1, 2)
    val MIGRATION_2_3 = Migration(2, 3)
    fun build() = builder.addMigrations(MIGRATION_1_2)
}'''}
        self.assertTrue(any('MIGRATION_2_3' in message for message in failures(audit_sources(source, value))))

    def test_main_thread_and_destructive_fallback_are_rejected(self):
        for call in ('allowMainThreadQueries', 'fallbackToDestructiveMigration', 'fallbackToDestructiveMigrationOnDowngrade'):
            source = {BASE + 'database/AppDatabase.kt': 'val db = builder.' + call + '()'}
            self.assertTrue(any('destructive migration' in message for message in failures(audit_sources(source, schema('id')))))

    def test_removed_credential_scan_call_is_rejected(self):
        source = {'app/src/androidTest/Test.kt': 'AppDatabase.encryptLegacySecrets(db)'}
        self.assertTrue(any('removed encryptLegacySecrets' in message for message in failures(audit_sources(source, schema('id')))))


if __name__ == '__main__':
    unittest.main()
