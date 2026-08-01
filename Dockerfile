# Reproducible Maven build environment for kiro-control-panel.
#
# The app itself is a Swing/AWT GUI and must run on the host (this container
# is headless — a JFrame dies at construction with HeadlessException here).
# Only `mvn` invocations belong in the container; everything else (reading,
# writing, editing files) happens directly on the host.
#
# Build once, then run long-lived and `docker exec` into it, bind-mounting
# the parent projects directory so edits made on the host are visible
# immediately:
#
#   docker build -t kiro-cp-maven .
#   docker run -d --name kiro-cp-maven -v ~/projects:/projects kiro-cp-maven tail -f /dev/null
#   docker exec kiro-cp-maven bash -c "cd /projects/kiro-control-panel && mvn -q test"
#
# See .claude/skills/ship-issue/SKILL.md for this repo's full build/verify/
# ship workflow.
FROM maven:3.9.11-amazoncorretto-24
