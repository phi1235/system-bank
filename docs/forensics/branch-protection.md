# Financial Forensics Branch Protection

Repository administrators should configure the following rule for `main` (and the production release branch when present):

- Require pull requests; direct pushes are disabled.
- Require the existing light CI checks and `Forensic scenario governance` when reported.
- Require at least one approval from the repository team responsible for money-flow regression review.
- Dismiss stale approvals after changes and require conversation resolution.
- Do not allow workflow tokens or the incident-to-scenario automation identity to bypass protection.
- Scenario automation may create only a draft branch/PR after the database scenario is human-confirmed; it never receives merge or deployment permission.

GitHub branch protection is external repository state and must be verified by a repository administrator after this file is merged.
