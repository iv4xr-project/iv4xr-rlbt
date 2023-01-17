# RLbT
Reinforcement Learning based coverage driven test case generation tool.

## Usage
To run RLbT use the following command

> java -jar rlbt.jar -$mode -burlapConfig $burlapConfigFile -sutConfig $sutConfigFile

where:
- $mode could be either trainingMode or testingMode
	- trainingMode could be either 'trainingMode' indicating single-agent or 'multiagentTrainingMode' indicating multi-agent architecture
- $burlapConfigFile is a file for configuring BURLAP Q-learning agent
- $sutConfigFile is a file for configuring Lab Recruits SUT

## RLbT mode
When RLbT is run in training mode, the agent plays for several episode and output a Q-table. RlbT supports both single and multi-agent training mode.
 
When RLbT is run in testing mode, the agent loads a learned Q-table and tests it on the SUT.

## BURLAP Q-learning configuration file
BURLAP Q-learning configuration file is a text file where each line contains a parameter and its value separated by an equal (=) symbol. Available parameters are:

- burlap.algorithm: only QLearning is available for now
- burlap.qlearning.qinit: initial Q-value to use everywhere
- burlap.qlearning.lr: value of learning rate. Learning rate determines to what extent newly acquired information overrides old information. A factor of 0 makes the agent learn nothing (exclusively exploiting prior knowledge), while a factor of 1 makes the agent consider only the most recent information (ignoring prior knowledge to explore possibilities).
- burlap.qlearning.gamma: value for gamma (discount factor). It determines the importance of future rewards. A factor of 0 will make the agent only considering current rewards, while a factor approaching 1 will make it strive for a long-term high reward.
- burlap.qlearning.epsilonval: value of epsilon parameter for Epsilon-Greedy algorithm. Here, Epsilon-Greedy method is used to balance exploration and exploitation while interacting with the Reinforcement Learning environment.
- burlap.qlearning.out_qtable: path to the Q-table
- burlap.num_of_episodes: number of episodes to run
- burlap.max_update_cycles: number of steps in each episode

## Lab Recruits SUT configuration file
Lab Recruits SUT configuration file is a text file where each line contains a parameter and its value separated by an equal (=) symbol. 
Depending on the training mode, specific SUT configuration file should be used. 

a) For single-agent training mode - Available parameters are:
- labrecruits.level_name: name of the file containing the Lab Recruits level (without csv extension)
- labrecruits.level_folder: folder where the Lab Recruits level is stored 
- labrecruits.execution_folder: Lab Recruits  folder
- labrecruits.agent_id: name of the agent in the Lab Recruits  level
- labrecruits.use_graphics: whether or not to enable graphic
- labrecruits.max_ticks_per_action: number of time to complete a goal
- labrecruits.max_actions_per_episode: number of action in each BURLAP episode
- labrecruits.target_entity_name: name of the entity in the level that the agent has to reach
- labrecruits.target_entity_type: type of the entity (Switch or Door)
- labrecruits.target_entity_property_name: name of the property of the entity that the agent has to check (isOn for a Switch and isOpen for the a Door)
- labrecruits.target_entity_property_value: value of the property the agent has to check
- labrecruits.search_mode : search mode is either 'CoverageOriented' or 'GoalOriented'. The RL agent aims to cover most entities in 'CoverageOriented' mode, while it tries to learn how to achieve a goal (such as, reaching a specific room) in 'GoalOriented' mode.  
- labrecruits.functionalCoverage='true'/'false' to switch on/off the calculation of achieved functional coverage during training sessions 
- labrecruits.rewardtype=Reward type is either 'Sparse' or 'CuriousityDriven'. In 'Sparse' reward type follows a classic RL approach based on the intrinsic sparse reward received from the environment. 'CuriousityDriven' RL approach follows a reward mechanism that enables the agent to explore the space of interactions in the game.


b) For multi-agent training mode - Available parameters are:
- labrecruits.level_name: name of the file containing the Lab Recruits level (without csv extension)
- labrecruits.level_folder: folder where the Lab Recruits level is stored 
- labrecruits.execution_folder: Lab Recruits  folder
- labrecruits.agentpassive_id: name of the passive agent in the Lab Recruits level
- labrecruits.agentactive_id: name of the active agent in the Lab Recruits level
- labrecruits.use_graphics: whether or not to enable graphic
- labrecruits.max_ticks_per_action: number of time to complete a goal
- labrecruits.max_actions_per_episode: number of action in each BURLAP episode
- labrecruits.target_entity_name: name of the entity in the level that the agent has to reach
- labrecruits.target_entity_type: type of the entity (Switch or Door)
- labrecruits.target_entity_property_name: name of the property of the entity that the agent has to check (isOn for a Switch and isOpen for the a Door)
- labrecruits.target_entity_property_value: value of the property the agent has to check
- labrecruits.search_mode : search mode is either 'CoverageOriented' or 'GoalOriented'. The RL agent aims to cover most entities in 'CoverageOriented' mode, while it tries to learn how to achieve a goal (such as, reaching a specific room) in 'GoalOriented' mode.  
- labrecruits.functionalCoverage='true'/'false' to switch on/off the calculation of achieved functional coverage during training sessions 
- labrecruits.rewardtype=Reward type is either 'Sparse' or 'CuriousityDriven'. In 'Sparse' reward type follows a classic RL approach based on the intrinsic sparse reward received from the environment. 'CuriousityDriven' RL approach follows a reward mechanism that enables the agent to explore the space of interactions in the game.

## Requirements for RLbT
The tool RLbT requires at least Java 11.
