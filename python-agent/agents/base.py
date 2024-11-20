from abc import ABC, abstractmethod

class BaseAgent(ABC):
    """Abstract base class for all agents."""
    def __init__(self, name: str, config: dict):
        self.name = name
        self.config = config

    @abstractmethod
    def run(self):
        """Method to execute the agent's task."""
        pass
