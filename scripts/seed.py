import os
import psycopg2

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "5432"))
DB_NAME = os.getenv("DB_NAME", "base_db")
DB_USER = os.getenv("DB_USER", "base")
DB_PASSWORD = os.getenv("DB_PASSWORD", "basepass")


def seed():
    conn = psycopg2.connect(host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD)
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM bases")
    (count,) = cur.fetchone()
    if count == 0:
        cur.execute(
            "INSERT INTO bases (player_id) VALUES (%s) ON CONFLICT DO NOTHING",
            ("player-1",),
        )
        conn.commit()
    cur.close()
    conn.close()


if __name__ == "__main__":
    seed()
