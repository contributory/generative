"""Execute the real rename DAO SQL on exported schema, without running Gradle."""

from contextlib import closing
import json
from pathlib import Path
import re
import unittest

import phase5_sqlite as checks

ROOT = Path(__file__).resolve().parents[1]
STORAGE = ROOT / 'app/src/main/kotlin/org/starfall/multigateway/shared/storage'


class ConversationRenameSqlTest(unittest.TestCase):
    def setUp(self):
        database = (STORAGE / 'database/AppDatabase.kt').read_text()
        version = re.search(r'\bversion\s*=\s*(\d+)', database)[1]
        schema = ROOT / 'app/schemas/org.starfall.multigateway.shared.storage.database.AppDatabase' / (version + '.json')
        self.entities = checks._entities(json.loads(schema.read_text()))
        calls, _ = checks._calls((STORAGE / 'database/dao/Daos.kt').read_text(), 'Query')
        self.sql = dict(calls)['renameConversation']

    def snapshot(self, db, table):
        return sorted(db.execute('SELECT * FROM ' + checks._q(table)).fetchall(), key=repr)

    def test_rename_preserves_every_child_and_other_conversations(self):
        with closing(checks._connect()) as db:
            checks._build(db, self.entities)
            checks._seed(db, self.entities)
            tables = set(self.entities) - {'conversations'}
            before = {table: self.snapshot(db, table) for table in tables}
            conversations = self.snapshot(db, 'conversations')
            db.execute(self.sql, dict(id='fixture-conversation-0', title='Renamed', updatedAt=999))
            self.assertEqual(before, {table: self.snapshot(db, table) for table in tables})
            after = self.snapshot(db, 'conversations')
            self.assertEqual([r for r in conversations if r[0] != 'fixture-conversation-0'],
                             [r for r in after if r[0] != 'fixture-conversation-0'])
            self.assertEqual(('Renamed', 999), db.execute(
                'SELECT title, updatedAt FROM conversations WHERE id=?', ('fixture-conversation-0',)).fetchone())
            self.assertEqual([], db.execute('PRAGMA foreign_key_check').fetchall())

    def test_missing_conversation_is_a_noop(self):
        with closing(checks._connect()) as db:
            checks._build(db, self.entities)
            checks._seed(db, self.entities)
            before = {table: self.snapshot(db, table) for table in self.entities}
            db.execute(self.sql, dict(id='absent', title='Missing', updatedAt=999))
            self.assertEqual(before, {table: self.snapshot(db, table) for table in self.entities})

    def test_repository_uses_targeted_dao_not_parent_replace(self):
        source = (STORAGE / 'ConversationRepository.kt').read_text()
        method = source.split('suspend fun renameConversation(', 1)[1].split('\n    suspend fun ', 1)[0]
        self.assertIn('dao.renameConversation(id, title, now)', method)
        self.assertNotIn('insertOrUpdate', method)
        self.assertNotIn('getConversationById', method)


if __name__ == '__main__':
    unittest.main()
