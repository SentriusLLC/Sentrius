import yaml
from langchain.chat_models import ChatOpenAI
from langchain_experimental.sql import SQLDatabaseSequentialChain
from langchain.sql_database import SQLDatabase
from agents.base_agent import BaseAgent

class SQLAgent(BaseAgent):
    """SQL Agent using SQLDatabaseSequentialChain."""
    def __init__(self, config_path: str):
        # Load configuration
        with open(config_path, "r") as file:
            config = yaml.safe_load(file)
        super().__init__("SQLAgent", config)

        self.db_url = config.get("database_url")
        self.questions_file = config.get("questions_file")
        self.model_name = config.get("model_name", "gpt-4")

        # Initialize LangChain components
        self.db = SQLDatabase.from_uri(self.db_url)
        self.llm = ChatOpenAI(model=self.model_name)
        self.chain = SQLDatabaseSequentialChain.from_llm(self.llm, self.db, verbose=True)

    def run(self):
        """Executes the agent: loads questions and runs against the database."""
        with open(self.questions_file, "r") as file:
            questions = yaml.safe_load(file)

        print(f"Running SQL Agent with {len(questions)} questions:")
        for idx, question in enumerate(questions, start=1):
            print(f"\nQuestion {idx}: {question}")
            response = self.chain.run(question)
            print(f"Answer: {response}")
