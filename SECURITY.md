# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

File Manager + is a privacy-first app. Security and privacy are critical to us.

If you believe you have found a security vulnerability, please report it
privately to the project maintainer. Do **not** open a public GitHub issue for
security matters.

When reporting, include as much of the following as possible:

- A description of the issue and its impact
- Steps to reproduce the vulnerability
- The affected versions and components
- Suggested fix or mitigation (if you have one)
- Your contact information

We will acknowledge receipt of your report, investigate, and keep you informed
of progress. We ask that you give us reasonable time to address the issue
before public disclosure.

## Security Best Practices for Contributors

- Never log or store SMB credentials (or any secrets) in plaintext.
- Never hardcode secrets, API keys, credentials, personal paths, or machine-specific SDK paths.
- Do not modify generated build output.
- Keep dependencies minimal and explain any new dependency.

## Scope

This policy covers the File Manager + Android application source in this
repository. Out of scope: third-party libraries and the Android platform itself.
