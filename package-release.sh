#!/usr/bin/env bash
#
# Prepara una cartella autoconsistente con tutto il necessario per eseguire
# RLbT su Minecraft su un'altra macchina (jar + config + testbench mineflayer).
#
# & "C:\Program Files\Git\bin\bash.exe" package-release.sh --zip
#
# Uso:
#   ./package-release.sh                        # crea dist-release/RLbT-minecraft
#   ./package-release.sh -o /path/to/out        # cartella di output diversa
#   ./package-release.sh --with-node-modules    # include node_modules (~400 MB)
#   ./package-release.sh --zip                  # crea anche l'archivio .zip
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_ROOT="$PROJECT_DIR/dist-release"
PKG_NAME="RLbT-minecraft"
WITH_NODE_MODULES=0
MAKE_ZIP=0

while [[ $# -gt 0 ]]; do
	case "$1" in
		-o|--output) OUT_ROOT="$2"; shift 2 ;;
		-n|--name) PKG_NAME="$2"; shift 2 ;;
		--with-node-modules) WITH_NODE_MODULES=1; shift ;;
		--zip) MAKE_ZIP=1; shift ;;
		-h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}"; exit 0 ;;
		*) echo "Opzione sconosciuta: $1" >&2; exit 1 ;;
	esac
done

PKG="$OUT_ROOT/$PKG_NAME"
JAR="$PROJECT_DIR/target/iv4xr-rlbt-1.0-jar-with-dependencies.jar"
TESTBENCH="$PROJECT_DIR/sut/minecraft/mineflayer-testbench"
CONFIG_DIR="$PROJECT_DIR/src/test/resources/configurations"
LEVELS_DIR="$PROJECT_DIR/src/test/resources/minecraft-levels"

info() { echo "[package] $*"; }
fail() { echo "[package] ERRORE: $*" >&2; exit 1; }

# --- controlli preliminari ------------------------------------------------
[[ -f "$JAR" ]] || fail "jar non trovato: $JAR
        Compila prima con:  mvn clean package -DskipTests"

[[ -d "$TESTBENCH/dist" ]] || fail "$TESTBENCH/dist non esiste.
        Compila il testbench con:  (cd \"$TESTBENCH\" && npm install && npm run build)"

for f in game.config mineAgent.config burlap_minecraft.config; do
	[[ -f "$CONFIG_DIR/$f" ]] || fail "config mancante: $CONFIG_DIR/$f"
done

# Livello referenziato da mineAgent.config (mine.level=...)
LEVEL_REL="$(grep -E '^[[:space:]]*mine\.level[[:space:]]*=' "$CONFIG_DIR/mineAgent.config" \
	| tail -1 | cut -d= -f2- | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
[[ -n "$LEVEL_REL" ]] || fail "mine.level non trovato in mineAgent.config"
[[ -f "$PROJECT_DIR/$LEVEL_REL" ]] || fail "livello referenziato inesistente: $LEVEL_REL"
info "livello in uso: $LEVEL_REL"

MINE_ADDRESS="$(grep -E '^[[:space:]]*mine\.address[[:space:]]*=' "$CONFIG_DIR/mineAgent.config" \
	| tail -1 | cut -d= -f2- | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

# --- costruzione pacchetto ------------------------------------------------
info "output: $PKG"
rm -rf "$PKG"
mkdir -p "$PKG/src/test/resources/configurations"
mkdir -p "$PKG/src/test/resources/minecraft-levels"
mkdir -p "$PKG/sut/minecraft/mineflayer-testbench"

info "copio il jar ($(du -h "$JAR" | cut -f1))..."
cp "$JAR" "$PKG/"

info "copio i config..."
cp "$CONFIG_DIR/game.config" \
   "$CONFIG_DIR/mineAgent.config" \
   "$CONFIG_DIR/burlap_minecraft.config" \
   "$PKG/src/test/resources/configurations/"

info "copio i livelli..."
cp "$LEVELS_DIR"/*.csv "$PKG/src/test/resources/minecraft-levels/"

info "copio il testbench mineflayer..."
TB_OUT="$PKG/sut/minecraft/mineflayer-testbench"
cp -r "$TESTBENCH/dist" "$TB_OUT/"
for f in package.json package-lock.json tsconfig.json config.json README.md LICENSE; do
	[[ -f "$TESTBENCH/$f" ]] && cp "$TESTBENCH/$f" "$TB_OUT/"
done

if [[ $WITH_NODE_MODULES -eq 1 ]]; then
	info "copio node_modules (~$(du -sh "$TESTBENCH/node_modules" 2>/dev/null | cut -f1))... può volerci un po'"
	cp -r "$TESTBENCH/node_modules" "$TB_OUT/"
else
	info "node_modules NON incluso (il destinatario dovra' lanciare 'npm install')"
fi

# --- script di avvio per il destinatario ----------------------------------
cat > "$PKG/run.sh" <<'EOF'
#!/usr/bin/env bash
# Avvia RLbT su Minecraft. Eseguire SEMPRE da questa cartella.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
if [[ ! -d sut/minecraft/mineflayer-testbench/node_modules ]]; then
	echo "node_modules mancante: eseguo npm install..."
	(cd sut/minecraft/mineflayer-testbench && npm install)
fi
java -jar iv4xr-rlbt-1.0-jar-with-dependencies.jar -game Minecraft "$@"
EOF
chmod +x "$PKG/run.sh"

cat > "$PKG/run.bat" <<'EOF'
@echo off
REM Avvia RLbT su Minecraft. Eseguire SEMPRE da questa cartella.
cd /d "%~dp0"
if not exist "sut\minecraft\mineflayer-testbench\node_modules" (
	echo node_modules mancante: eseguo npm install...
	pushd sut\minecraft\mineflayer-testbench
	call npm install
	popd
)
java -jar iv4xr-rlbt-1.0-jar-with-dependencies.jar -game Minecraft %*
EOF

# --- istruzioni -----------------------------------------------------------
cat > "$PKG/LEGGIMI.md" <<EOF
# RLbT - Minecraft: pacchetto di esecuzione

## Requisiti
- Java 11 o superiore (\`java -version\`)
- Node.js + npm nel PATH (il launcher avvia da solo il testbench mineflayer)
- Un server Minecraft vanilla raggiungibile, con \`online-mode=false\` e il bot (\`Bot\`) OP

## Primo avvio
$(if [[ $WITH_NODE_MODULES -eq 0 ]]; then
	echo '1. Installa le dipendenze Node (una volta sola):'
	echo
	echo '       cd sut/minecraft/mineflayer-testbench'
	echo '       npm install'
	echo '       cd ../../..'
	echo
	echo '2. Avvia:'
else
	echo 'Le dipendenze Node sono gia incluse. Avvia direttamente:'
fi)

       ./run.sh          # Linux / macOS / Git Bash
       run.bat           # Windows (cmd / PowerShell)

Equivale a lanciare, **dalla root di questa cartella**:

       java -jar iv4xr-rlbt-1.0-jar-with-dependencies.jar -game Minecraft

> IMPORTANTE: i path dei config e del testbench sono relativi alla directory
> corrente. Lanciando il jar da un'altra cartella l'esecuzione fallisce.

## Configurazione
Tutti i parametri stanno in \`src/test/resources/configurations/\`:

| File | Cosa contiene |
|---|---|
| \`game.config\` | modalita (\`game.mode\`: training / testing / random), quali altri config usare, flag baseline |
| \`mineAgent.config\` | indirizzo server, livello, reward type, arma, tick/azioni per episodio |
| \`burlap_minecraft.config\` | algoritmo RL, numero di episodi, learning rate, gamma, epsilon |

**Da cambiare quasi sicuramente**: \`mine.address\` in \`mineAgent.config\`
(valore attuale: \`${MINE_ADDRESS:-non impostato}\`) con l'indirizzo del proprio server,
nella forma \`host:porta\`.

Il livello attualmente selezionato e \`$LEVEL_REL\`.
Gli altri livelli disponibili sono in \`src/test/resources/minecraft-levels/\`:
cambia \`mine.level\` in \`mineAgent.config\` per usarne uno diverso.

## Output
I risultati vengono scritti in \`rlbt-files/minecraft-results/\`, creata
automaticamente al primo avvio.
EOF

info "fatto: $PKG ($(du -sh "$PKG" | cut -f1))"

if [[ $MAKE_ZIP -eq 1 ]]; then
	ZIP="$OUT_ROOT/$PKG_NAME.zip"
	info "creo l'archivio $ZIP ..."
	rm -f "$ZIP"
	if command -v zip >/dev/null 2>&1; then
		(cd "$OUT_ROOT" && zip -rq "$PKG_NAME.zip" "$PKG_NAME")
	else
		powershell -NoProfile -Command \
			"Compress-Archive -Path '$(cygpath -w "$PKG" 2>/dev/null || echo "$PKG")\\*' -DestinationPath '$(cygpath -w "$ZIP" 2>/dev/null || echo "$ZIP")'"
	fi
	info "archivio pronto: $ZIP ($(du -h "$ZIP" | cut -f1))"
fi
