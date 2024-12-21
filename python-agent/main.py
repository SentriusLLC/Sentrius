import argparse
from agents.sql_agent.sql_agent import SQLAgent

def main():
    parser = argparse.ArgumentParser(description="Run selected agent.")
    parser.add_argument(
        "agent",
        choices=["sql_agent"],
        help="Select the agent to run."
    )
    args = parser.parse_args()

    if args.agent == "sql_agent":
        print("Initializing SQL Agent...")
        sql_agent = SQLAgent("agents/sql_agent/config.yaml")
        sql_agent.run()
    else:
        print("Unknown agent. Exiting.")

if __name__ == "__main__":
    main()
