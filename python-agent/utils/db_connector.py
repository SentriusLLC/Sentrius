import os
import sqlalchemy
from sqlalchemy.orm import sessionmaker

def get_db_session(database_url: str):
"""Returns a database session."""
engine = sqlalchemy.create_engine(database_url)
Session = sessionmaker(bind=engine)
return Session()
