# RLbT
Reinforcement Learning based coverage driven test case generation tool.

## Usage
To run RLbT use the following command

> java -jar rlbt.jar -$mode -burlapConfig $burlapConfigFile -sutConfig $sutConfigFile

where:
- $mode could be either trainingMode or testingMode
- $burlapConfigFile is a file for configuring BURLAP Q-learning agent
- $sutConfigFile is a file for configuring Lab Recruits SUT

## RLbT mode
When RLbT is run in training mode, the agent plays for several episode and output a Q-table.

When RLbT is run in testing mode, the agent loads a learned Q-table and tests it on the SUT.

## BURLAP Q-learning configuration file
BURLAP Q-learning configuration file is a text file where each line contains a parameter and its value separated by an equal (=) symbol. Available parameters are:

- burlap.algorithm: only QLearning is available for now
- burlap.qlearning.qinit: initial Q-value to user everywhere
- burlap.qlearning.lr: learning rate
- burlap.qlearning.gamma: value for gamma
- burlap.qlearning.out_qtable: path to the Q-table
- burlap.num_of_episodes: number of episodes to run
- burlap.max_update_cycles: number of steps in eqch episode

## Lab Recruits SUT configuration file
Lab Recruits SUT configuration file is a text file where each line contains a parameter and its value separated by an equal (=) symbol. Available parameters are:

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

## Requirements for RLbT
The tool RLbT requires at least Java 11.
