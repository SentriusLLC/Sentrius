# Screenshot Suggestions for Sentrius Documentation

This document outlines suggested screenshots to enhance the Sentrius documentation and improve user understanding.

## Currently Used Screenshots

1. **dashboard.png** (3746 x 1961) - Main dashboard view, used in README header
2. **mainscreen.png** (3813 x 1913) - Main screen view (NOT currently used in new README)
3. **ssh.png** (608 x 123) - SSH session interface
4. **agentdesigner.png** (2760 x 1931) - Agent Designer interface

## Recommended Additional Screenshots

### High Priority

1. **Quick Start Deployment**
   - **File:** `docs/images/kubernetes-deployment.png`
   - **Content:** Screenshot showing successful Kubernetes deployment with port-forward commands
   - **Usage:** In README Quick Start section and DEPLOYMENT.md
   - **Purpose:** Help users visualize successful deployment

2. **Enclave Management**
   - **File:** `docs/images/enclave-management.png`
   - **Content:** Screenshot of the enclave management interface showing host groups and access controls
   - **Usage:** In README Key Features section
   - **Purpose:** Showcase the enclave feature visually

3. **Self-Healing Configuration**
   - **File:** `docs/images/self-healing-config.png`
   - **Content:** Self-healing configuration UI showing patching policies
   - **Usage:** In INTEGRATIONS.md Self-Healing section
   - **Purpose:** Help users understand self-healing configuration options

4. **Self-Healing Session View**
   - **File:** `docs/images/self-healing-session.png`
   - **Content:** Active healing session showing agent logs and status
   - **Usage:** In INTEGRATIONS.md Self-Healing section
   - **Purpose:** Show users what to expect during healing process

### Medium Priority

5. **Integration Settings**
   - **File:** `docs/images/integration-settings.png`
   - **Content:** Integration settings page showing GitHub/JIRA token configuration
   - **Usage:** In INTEGRATIONS.md
   - **Purpose:** Guide users through integration setup

6. **Rules Engine**
   - **File:** `docs/images/rules-engine.png`
   - **Content:** Dynamic rules configuration interface
   - **Usage:** In README or dedicated rules documentation
   - **Purpose:** Showcase dynamic rule enforcement capabilities

7. **Session Monitoring**
   - **File:** `docs/images/session-monitoring.png`
   - **Content:** Real-time SSH session monitoring view with active sessions
   - **Usage:** In README or dedicated monitoring documentation
   - **Purpose:** Show live monitoring capabilities

### Low Priority

8. **Python Agent Console**
   - **File:** `docs/images/python-agent-console.png`
   - **Content:** Terminal showing Python agent running in test mode
   - **Usage:** In CUSTOM_AGENTS.md
   - **Purpose:** Help developers understand agent development workflow

9. **Helm Chart Testing**
   - **File:** `docs/images/helm-testing.png`
   - **Content:** Terminal output showing successful helm chart tests
   - **Usage:** In DEPLOYMENT.md and DEVELOPMENT.md
   - **Purpose:** Show testing workflow

10. **Build Process**
    - **File:** `docs/images/maven-build.png`
    - **Content:** Terminal showing successful Maven build
    - **Usage:** In DEVELOPMENT.md
    - **Purpose:** Help new developers understand build process

## Suggestions for Existing Screenshots

### Potentially Replace/Update

- **mainscreen.png** is currently unused in the new README. Consider:
  - Replace with more specific feature screenshots
  - OR use it to show main navigation/menu structure
  - OR update README to include it as an overview screenshot

### Image Optimization

All PNG files are quite large (15KB - 223KB). Consider:
- Optimizing images for web (reduce resolution for documentation)
- Using compressed PNGs or WebP format
- Keeping originals in a separate folder

## Implementation Priority

**Phase 1 (Immediate):**
- Add mainscreen.png to README or document where it should be used
- Create Quick Start Deployment screenshot

**Phase 2 (Near-term):**
- Enclave Management screenshot
- Self-Healing Configuration and Session screenshots
- Integration Settings screenshot

**Phase 3 (As needed):**
- Rules Engine, Session Monitoring
- Development workflow screenshots

## Screenshot Guidelines

When creating new screenshots:

1. **Resolution:** Use 1920x1080 or similar 16:9 aspect ratio
2. **Content:** Show realistic data (no empty states unless demonstrating initial setup)
3. **Annotations:** Consider adding arrows or highlights for key UI elements
4. **Consistency:** Use same theme/color scheme across all screenshots
5. **Accessibility:** Ensure text is readable at various sizes
6. **Privacy:** Remove any sensitive information (real usernames, IPs, tokens)

## Integration with Documentation

Update the following files when adding new screenshots:

- `README.md` - Feature highlights, Quick Start
- `DEPLOYMENT.md` - Deployment process, configuration
- `DEVELOPMENT.md` - Build process, testing
- `CUSTOM_AGENTS.md` - Agent development workflow
- `INTEGRATIONS.md` - Integration setup, self-healing

## Maintenance

- Review screenshots quarterly for accuracy with current UI
- Update screenshots when major UI changes occur
- Keep a changelog of screenshot updates in this file

---

**Last Updated:** 2025-12-23
**Maintainer:** Sentrius Documentation Team
