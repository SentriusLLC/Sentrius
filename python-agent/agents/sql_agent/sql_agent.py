import yaml
import logging
import os
from langchain_community.chat_models import ChatOpenAI
from langchain_experimental.sql import SQLDatabaseSequentialChain
from langchain_community.utilities import SQLDatabase
from ..base import BaseAgent

logger = logging.getLogger(__name__)


class SQLAgent(BaseAgent):
    """SQL Agent using SQLDatabaseSequentialChain with Sentrius integration."""
    
    def __init__(self, config_path: str = None):
        # Load SQL-specific configuration
        if config_path:
            with open(config_path, "r") as file:
                sql_config = yaml.safe_load(file)
        else:
            sql_config = {}
            
        super().__init__("SQL Agent", config_path=config_path)
        
        # Store SQL-specific configuration
        self.sql_config = sql_config
        self.db_url = sql_config.get("database_url")
        self.questions_file = sql_config.get("questions_file")
        self.model_name = sql_config.get("model_name", "gpt-4")

        # Initialize LangChain components if config is provided
        if self.db_url:
            self.db = SQLDatabase.from_uri(self.db_url)
            try:
                # Try to initialize LLM with API key
                openai_api_key = os.getenv('OPENAI_API_KEY')
                if openai_api_key:
                    self.llm = ChatOpenAI(model=self.model_name, openai_api_key=openai_api_key)
                    self.chain = SQLDatabaseSequentialChain.from_llm(self.llm, self.db, verbose=True)
                else:
                    logger.warning("No OPENAI_API_KEY found, LLM features will be disabled")
                    self.llm = None
                    self.chain = None
            except Exception as e:
                logger.error(f"Failed to initialize LLM: {e}")
                self.llm = None
                self.chain = None
        else:
            self.db = None
            self.llm = None
            self.chain = None
            logger.warning("No database URL provided, SQL operations will be limited")

    def execute_task(self):
        """Execute the SQL Agent's specific task."""
        logger.info(f"Running {self.name}...")
        
        # Submit task start event
        self.submit_provenance(
            event_type="SQL_TASK_START",
            details={
                "task_type": "sql_analysis",
                "db_url": self.db_url,
                "model_name": self.model_name,
                "questions_file": self.questions_file
            }
        )
        
        if not self.chain:
            logger.error("SQL chain not initialized - missing database configuration")
            self.submit_provenance(
                event_type="SQL_TASK_ERROR",
                details={
                    "task_type": "sql_analysis",
                    "error": "SQL chain not initialized",
                    "error_type": "ConfigurationError"
                }
            )
            return
        
        # Load and process questions if questions file is provided
        if self.questions_file:
            try:
                with open(self.questions_file, "r") as file:
                    questions = yaml.safe_load(file)

                logger.info(f"Running SQL Agent with {len(questions)} questions:")
                
                for idx, question in enumerate(questions, start=1):
                    logger.info(f"Question {idx}: {question}")
                    
                    try:
                        response = self.chain.run(question)
                        logger.info(f"Answer: {response}")
                        
                        # Submit successful query event
                        self.submit_provenance(
                            event_type="SQL_QUERY_SUCCESS",
                            details={
                                "question_number": idx,
                                "question": question,
                                "response_length": len(str(response))
                            }
                        )
                        
                    except Exception as e:
                        logger.error(f"Failed to process question {idx}: {e}")
                        
                        # Submit query error event
                        self.submit_provenance(
                            event_type="SQL_QUERY_ERROR",
                            details={
                                "question_number": idx,
                                "question": question,
                                "error": str(e),
                                "error_type": type(e).__name__
                            }
                        )
                        
            except Exception as e:
                logger.error(f"Failed to load questions file: {e}")
                self.submit_provenance(
                    event_type="SQL_TASK_ERROR",
                    details={
                        "task_type": "sql_analysis",
                        "error": f"Failed to load questions: {str(e)}",
                        "error_type": type(e).__name__
                    }
                )
                return
        else:
            logger.info("No questions file provided, SQL Agent ready for interactive use")
        
        # Submit task completion event
        self.submit_provenance(
            event_type="SQL_TASK_COMPLETE",
            details={
                "task_type": "sql_analysis",
                "status": "success"
            }
        )
        
        logger.info("SQL Agent task execution completed")
