# RLbT
Reinforcement Learning based coverage driven test case generation tool.

RLbT drives an agent through a game under test (SUT) with reinforcement learning, and measures the
coverage it achieves. Two SUTs are supported:

- **Minecraft**: a Mineflayer bot on a real vanilla
  Minecraft server, driven over HTTP through [MineflayerTestbench](sut/minecraft/mineflayer-testbench).
- **LabRecruits**: the original SUT, still supported (single and multi agent).

Both tabular **Q-learning** and **Deep Q-learning (DQN)** are available; which one runs is decided
by `burlap.algorithm` in the BURLAP config file, not by the command line.

---

## Quick start: Minecraft (MineAgent)

### Requirements

- **Java 11+** and **Maven**
- **Node.js 18+** and **npm** (for the testbench)
- A running **vanilla Minecraft server** with `online-mode=false` (see
  [sut/minecraft/SERVER.md](sut/minecraft/SERVER.md) for the one used during development).
  The bot runs server commands, so it must be **OP**ed (default username: `Bot`).
- **Python 3** with `numpy` and `matplotlib` (`pip install numpy matplotlib`) [_only if you want to
  render the coverage heatmaps_]

### 1. Build everything (once)

```bash
# 1) the Java bridge to the testbench, into the local Maven repository
cd sut/minecraft/minecraftlib && mvn install -DskipTests && cd ../../..

# 2) the Node testbench (the SUT)
cd sut/minecraft/mineflayer-testbench && npm i && npm run build && cd ../../..

# 3) RLbT itself (jar)
mvn package -DskipTests
```

### 2. Point the agent at your Minecraft server

The only thing you normally have to edit is the server address in
`src/test/resources/configurations/mineAgent.config`:

```
mine.address=localhost:25565    # host:port of the Minecraft server (the port is required)
```

Everything else already has working defaults (level, reward, weapon, budgets).

### 3. Run

```bash
java -jar target/iv4xr-rlbt-1.0-jar-with-dependencies.jar -game Minecraft
```

`RlbtLauncher` starts the testbench itself (`npm run start`) and waits for its
`/status` endpoint; the agent then asks the testbench to log its bot into `mine.address`, builds
the arena from the level CSV, and runs the training episodes. With the shipped defaults this is a **training** run of the **RL agent** on the
`outdoor2_skeleton2` arena, with tabular Q-learning and 200 episodes.

The Minecraft server is the **only** process you have to start yourself.

> ⚠️ **`mvn compile` does not rebuild the jar.** Always `mvn package` before launching from the jar,
> otherwise you run the code of the previous `package`.
### 4. Where the results go

```
rlbt-files/minecraft-results/<level-name>/rlbt/<systemtime>/
```

(`<level-name>` is the basename of `mine.level` without `.csv`; `rlbt` becomes `baseline` for
baseline runs.)

| File | Content                                                                                             |
|---|-----------------------------------------------------------------------------------------------------|
| `ticks.csv` | per-tick telemetry: agent HP and position, mob position, distance, phase (input of the heatmap)     |
| `actions.csv` | per-action: action, goal outcome, HP before/after, damage, `hit_landed`, state buckets before/after |
| `summary.txt` | hits attempted/landed, efficiency, damage dealt/taken, kills, deaths, episodes                      |
| `episodeSummary.txt` | per episode: actions, total reward, time, coverage metrics, cumulative state-action coverage        |
| `qtable.ser` / `qtable.txt` | the learned Q-table (serialized and human readable)                                                 |
| `episode*.ser` | BURLAP episodes                                                                                     |

### 5. Optional: spatial coverage heatmap

Generated afterwards from `ticks.csv` plus the level CSV (1 px = 1 block):

```bash
./generate_minecraft_heatmap.sh rlbt-files/minecraft-results/arena/rlbt/<systemtime>
```

or directly:

```bash
python src/main/resources/scripts/heatmap_minecraft.py \
  --trace rlbt-files/minecraft-results/<level>/rlbt/<systemtime>/ticks.csv \
  --level src/test/resources/minecraft-levels/<level>.csv \
  --width 20 --height 20 --padding 1 --upscale 20 \
  -o rlbt-files/minecraft-results/<level>/rlbt/<systemtime>/heatmap.png
```

---

## Minecraft configuration files

What runs is decided by three files, not by the command line.

### `src/test/resources/configurations/game.config`

| Parameter | Meaning |
|---|---|
| `game.mode` | `training` / `testing` / `random` (`testing` is not implemented yet for Minecraft) |
| `game.mineAgentUseBaseline` | `false` = RL agent, `true` = scripted baseline (non-RL reference run) |
| `game.mineAgentSutConfig` | path of the Minecraft SUT config |
| `game.mineAgentBurlapConfig` | path of the Minecraft BURLAP config (falls back to `game.burlapConfig`) |

### `src/test/resources/configurations/mineAgent.config`

| Parameter | Default | Meaning |
|---|---|---|
| `mine.address` | `localhost:25565` | `host:port` of the Minecraft server (the port is required) |
| `mine.level` | `.../minecraft-levels/outdoor2_skeleton2.csv` | CSV describing the arena; also names the results folder (the built-in fallback, used when the key is absent, is `arena.csv`) |
| `mine.testbenchUrl` | `http://localhost:3000` | HTTP API exposed by the testbench |
| `mine.mob_tag` | `mob1` | tag of the mob the agent fights, as written in the level CSV |
| `mine.max_ticks_per_action` | `120` | tick budget for one action to reach its goal |
| `mine.max_actions_per_episode` | `30` | actions per episode |
| `mine.reward_type` | `CoverageOriented` | `CoverageOriented` (bonus `β/√N` on new state-action quadruples) or `CombatOriented` (damage dealt − damage taken) |
| `mine.weapon` | `iron_sword` | weapon given to the agent; changing it changes the difficulty of the scenario, not just a label |

Ready-made arenas are in `src/test/resources/minecraft-levels/` (`arena`, `outdoor1_*`,
`outdoor2_*`, one per mob type).

---

## Quick start: LabRecruits

```bash
mvn package -DskipTests
java -jar target/iv4xr-rlbt-1.0-jar-with-dependencies.jar -game LabRecruits
```

`game.config` selects the mode (`game.mode`), single vs multi agent (`game.lrSingleAgent`), the SUT
config (`game.lrSingleAgentSutConfig` / `game.lrMultiAgentSutConfig`) and the BURLAP config
(`game.burlapConfig`). The equivalent explicit invocation is:

```bash
java -cp target/iv4xr-rlbt-1.0-jar-with-dependencies.jar eu.fbk.iv4xr.rlbt.RlbtMain \
  -trainingMode \
  -burlapConfig src/test/resources/configurations/burlap_test.config \
  -sutConfig src/test/resources/configurations/lrLevelSingleAgent.config
```

In **training mode** the agent plays several episodes and outputs a Q-table; in **testing mode** it
loads a learned Q-table and tests it on the SUT. The other modes are `-testingMode` and
`-randomMode` (random exploration, the non-learning baseline); multi agent uses a different entry
point:

```bash
java -cp target/iv4xr-rlbt-1.0-jar-with-dependencies.jar eu.fbk.iv4xr.rlbt.RlbtMultiAgentMain \
  -multiagentTrainingMode \
  -burlapConfig src/test/resources/configurations/burlap_test.config \
  -sutConfig src/test/resources/configurations/lrLevelMultiAgent.config
```

In testing mode `burlap.algorithm` must be the same used for training, otherwise the tool tries
to deserialize the wrong model file (`qtable.ser` vs `qnetwork.ser`).

### BURLAP configuration file

A text file where each line contains a parameter and its value separated by an equal (=) symbol:

- `burlap.algorithm`: `QLearning` or `DeepQLearning`
- `burlap.qlearning.qinit`: initial Q-value to use everywhere
- `burlap.qlearning.lr`: learning rate
- `burlap.qlearning.gamma`: discount factor 
- `burlap.qlearning.epsilonval`: epsilon of the Epsilon-Greedy algorithm, used to balance exploration and exploitation
- `burlap.qlearning.out_qtable`: path to the Q-table
- `burlap.num_of_episodes`: number of episodes to run
- `burlap.max_update_cycles`: number of steps in each episode
- DQN only: `burlap.qlearning.dqn_lr`, `burlap.qlearning.epsilonmin`, `burlap.network.hidden_size`,
  `burlap.network.replay_buffer_capacity`, `burlap.network.batch_size`,
  `burlap.network.min_replay_size`, `burlap.network.target_update_frequency`

### LabRecruits SUT configuration file

One `parameter=value` per line; the applicable parameters depend on the training mode.

a) **Single-agent training mode**:
- `labrecruits.level_name`: name of the file containing the Lab Recruits level (without csv extension)
- `labrecruits.level_folder`: folder where the Lab Recruits level is stored
- `labrecruits.execution_folder`: Lab Recruits folder
- `labrecruits.agent_id`: name of the agent in the Lab Recruits level
- `labrecruits.use_graphics`: whether or not to enable graphics
- `labrecruits.max_ticks_per_action`: number of time to complete a goal
- `labrecruits.max_actions_per_episode`: number of actions in each BURLAP episode
- `labrecruits.target_entity_name`: name of the entity in the level that the agent has to reach
- `labrecruits.target_entity_type`: type of the entity (Switch or Door)
- `labrecruits.target_entity_property_name`: name of the property of the entity that the agent has to check (isOn for a Switch and isOpen for a Door)
- `labrecruits.target_entity_property_value`: value of the property the agent has to check
- `labrecruits.search_mode`: `CoverageOriented` or `GoalOriented`. The RL agent aims to cover most entities in 'CoverageOriented' mode, while it tries to learn how to achieve a goal (such as reaching a specific room) in 'GoalOriented' mode
- `labrecruits.functionalCoverage`: `true`/`false` to switch on/off the calculation of achieved functional coverage during training sessions
- `labrecruits.rewardtype`: `Sparse` or `CuriousityDriven`. 'Sparse' follows a classic RL approach based on the intrinsic sparse reward received from the environment; 'CuriousityDriven' follows a reward mechanism that enables the agent to explore the space of interactions in the game

b) **Multi-agent training mode**: the same parameters, except that `labrecruits.agent_id` is
replaced by `labrecruits.agentpassive_id` (passive agent) and `labrecruits.agentactive_id`
(active agent).

---

## Further documentation

- [`sut/minecraft/mineflayer-testbench/README.md`](sut/minecraft/mineflayer-testbench/README.md) —
  the SUT: HTTP API, level CSV format, configuration.
- [`sut/minecraft/minecraftlib/README.md`](sut/minecraft/minecraftlib/README.md) — the Java ↔
  testbench bridge.

## References
<a id="1">[1]</a>
R. Ferdous, F. M. Kifetew, D. Prandi, A. Susi.
*Towards Agent-Based Testing of 3D Games using Reinforcement Learning.*
37th IEEE/ACM International Conference on Automated Software Engineering 2022.
[doi:[10.1007/978-3-030-88106-1_5](https://doi.org/10.1145/3551349.3560507)](https://dl.acm.org/doi/abs/10.1145/3551349.3560507).
