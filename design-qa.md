source visual truth path: C:\Users\Administrator\.codex\generated_images\019efec6-66be-7fe1-a4f4-41ca3d4aab3e\ig_07d17b89bc4b0c57016a3d24ea7ef881978e952243a860d5f7.png
login screenshot path: C:\Users\Administrator\Desktop\xingyan\design-qa-assets\login-desktop.png
login mobile screenshot path: C:\Users\Administrator\Desktop\xingyan\design-qa-assets\login-mobile.png
login password-toggle screenshot path: C:\Users\Administrator\Desktop\xingyan\design-qa-assets\login-password-toggle.png
authenticated workbench screenshot path: C:\Users\Administrator\Desktop\xingyan\design-qa-assets\app-workbench-light.png
viewport: login desktop 1440x1024, login mobile 390x844, workbench desktop 1440x1024
state: unauthenticated /login; authenticated workbench rendered with local mocked /api responses for visual QA only

full-view comparison evidence: the login page keeps the quiet centered form on a cool gray background, with title, explicit labels, one primary login button, and copyright footer. The authenticated workbench now uses the same restrained light surface language: white panels, blue primary actions, light borders, and no decorative customer preview on the unauthenticated screen.

focused region comparison evidence: the password field was inspected after clicking the visibility control; the input type changed to text and the control label changed to "隐藏". Workbench header, menu, statistics, tabs, filters, table rows, and action buttons were checked in the rendered screenshot for overlap and visual consistency.

findings:
- No P0/P1/P2 findings.
- P3 verification boundary: authenticated workbench screenshot used mocked API data to validate layout and style. It is not a real backend or Casdoor acceptance run.

patches made since previous QA pass:
- Added password visibility toggle to the business login form without changing the Casdoor headless auth flow.
- Restyled the authenticated application shell to match the simplified login page: light top bar, white page header, quieter menus, plain button text, and blue primary actions.
- Removed decorative emoji-style UI labels from the main workflow.
- Preserved Authorization Code + PKCE S256, token storage, and Axios Bearer injection behavior.

verification:
- npm test: passed 5/5
- npm run build: passed
- runtime source auth scan excluding regression tests: no grant_type=password, no client_secret, no login/oauth/authorize, no Math.random, no challengeMethod, no old "仅查看本人负责客户" text
- browser visual check: password toggle changed input type to text; workbench rendered with rows and no old refresh emoji

final result: passed
