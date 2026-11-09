# Installing katachi

These are the steps for an AI agent to install [katachi](https://tbsten.github.io/katachi) in a Gradle project. The steps themselves live in the **katachi skill**, one section per file, so read them from there instead of from this page.

1. **Install the katachi skill.** Ask the user to run one of these (it adds the skill to their agent's skills), or run it yourself once they agree.

   ```sh
   gh skill install TBSten/katachi katachi
   npx skills add TBSten/katachi --skill katachi
   ```

2. **Follow the skill.** Its `SKILL.md` sends a first-time installation to `references/setup/README.md`, which lists the sections in order. Read one section at a time.

The steps run on the `katachi` command (`brew install tbsten/tap/katachi`); the setup's first section checks that it is there.

**If your agent cannot use skills**, read the same files from the repository, starting at https://raw.githubusercontent.com/TBSten/katachi/main/skills/katachi/SKILL.md . Links inside are relative to `skills/katachi/`. The steps are in English; the checklist and report they set up can be Japanese (step 0-2).

Once katachi is set up, the same skill covers adding to and updating the definition, upgrading katachi, and fixing violations.
