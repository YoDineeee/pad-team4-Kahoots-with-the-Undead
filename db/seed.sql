-- Seed sample data only if tables are empty.
INSERT INTO bases (player_id, rooms, facilities, storage_capacity, kiki_present, kiki_happiness)
SELECT 'player-1', '[]'::jsonb, '[]'::jsonb, 10, TRUE, 50
WHERE NOT EXISTS (SELECT 1 FROM bases);


