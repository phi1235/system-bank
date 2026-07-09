/**
 * Bank System UI — product-grade mockups
 * Customer: Internet Banking (header + footer)
 * Admin: Back Office + RBAC modules
 */
const SCREENS = {
  gallery: {},
  // Customer auth
  login: {},
  register: {},
  mfa: {},
  // Customer IB app
  home: {},
  accounts: {},
  transfer: {},
  history: {},
  profile: {},
  cards: {},
  wealth: {},
  support: {},
  // Admin auth + BO
  "admin-login": {},
  "admin-overview": {},
  "admin-customers": {},
  "admin-accounts": {},
  "admin-transfers": {},
  "admin-rbac": {},
  "admin-audit": {},
  "admin-risk": {},
};

const IB_NAV_ACTIVE = {
  home: "home",
  accounts: "accounts",
  transfer: "payments",
  history: "payments",
  profile: "profile",
  cards: "cards",
  wealth: "more",
  support: "more",
};

const BO_NAV_ACTIVE = {
  "admin-overview": "overview",
  "admin-customers": "customers",
  "admin-accounts": "accounts",
  "admin-transfers": "transfers",
  "admin-rbac": "rbac",
  "admin-audit": "audit",
  "admin-risk": "risk",
};

function go(name) {
  if (!SCREENS[name]) name = "gallery";

  document.querySelectorAll(".screen").forEach((el) => el.classList.remove("active"));
  const el = document.getElementById(`screen-${name}`);
  if (el) el.classList.add("active");

  // IB header active
  const ibKey = IB_NAV_ACTIVE[name];
  document.querySelectorAll("[data-ib-nav]").forEach((n) => {
    n.classList.toggle("active", n.dataset.ibNav === ibKey);
  });

  // BO sidenav active
  const boKey = BO_NAV_ACTIVE[name];
  document.querySelectorAll("[data-bo-nav]").forEach((n) => {
    n.classList.toggle("active", n.dataset.boNav === boKey);
  });

  // Close details dropdowns
  document.querySelectorAll("details.nav-more").forEach((d) => {
    d.open = false;
  });

  history.replaceState(null, "", `#${name}`);
  window.scrollTo(0, 0);
}

function initOtpInputs() {
  document.querySelectorAll(".otp-row").forEach((row) => {
    const inputs = [...row.querySelectorAll("input")];
    inputs.forEach((input, i) => {
      input.addEventListener("input", () => {
        input.value = input.value.replace(/\D/g, "").slice(0, 1);
        if (input.value && inputs[i + 1]) inputs[i + 1].focus();
      });
      input.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !input.value && inputs[i - 1]) inputs[i - 1].focus();
      });
    });
  });
}

function wireNav() {
  document.querySelectorAll("[data-go]").forEach((el) => {
    el.addEventListener("click", (e) => {
      e.preventDefault();
      go(el.dataset.go);
    });
  });
}

document.addEventListener("DOMContentLoaded", () => {
  wireNav();
  initOtpInputs();
  const hash = (location.hash || "#gallery").slice(1);
  go(SCREENS[hash] ? hash : "gallery");
});
