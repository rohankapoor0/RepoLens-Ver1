---
name: "RepoLens"
description: "Airy, light-first, Apple-inspired developer design system for GitHub repository hygiene evaluation, featuring soft pastel washes, obsidian pill CTAs, and crisp bento cards."
---

## Overview

RepoLens is a developer-first repository hygiene and standards evaluator. Its aesthetic combines **Apple's** generous whitespace, crisp display typography, and frosted navigation with an **airy light-first** palette (`#f5f6fa` canvas, `#ffffff` elevated bento cards, `#111318` obsidian pill CTAs) and soft lavender-to-ice-blue atmospheric washes.

Light mode is the default experience, with a seamless Dark mode (`#0b0e14` canvas, `#141820` cards, `#262c38` borders) available via the header toggle.

## Colors

RepoLens uses high-contrast obsidian ink (`#111318`) on crisp white and cool off-white surfaces, paired with soft pastel chart tones (`#7c8cf8`, `#3b82f6`, `#f4b459`, `#f26464`) and WCAG AA-compliant semantic badge pairs.

```yaml
colors:
  accent-blue: "#2563eb"
  tier-excellent: "#7c8cf8"
  tier-good: "#3b82f6"
  tier-fair: "#f4b459"
  tier-needs-work: "#f26464"
  badge-excellent-bg: "#dcfce7"
  badge-excellent-fg: "#166534"
  badge-good-bg: "#dbeafe"
  badge-good-fg: "#1e40af"
  badge-fair-bg: "#fef3c7"
  badge-fair-fg: "#92400e"
  badge-needs-bg: "#fee2e2"
  badge-needs-fg: "#991b1b"
  light:
    primary: "#111318"
    on-primary: "#ffffff"
    surface: "#f5f6fa"
    surface-container: "#ffffff"
    surface-container-high: "#f9fafb"
    on-surface: "#111318"
    on-surface-variant: "#4b5563"
    outline: "#e5e7eb"
    outline-subtle: "#f0f2f5"
  dark:
    primary: "#f3f4f6"
    on-primary: "#111318"
    surface: "#0b0e14"
    surface-container: "#141820"
    surface-container-high: "#1c222d"
    on-surface: "#f3f4f6"
    on-surface-variant: "#9ca3af"
    outline: "#262c38"
    outline-subtle: "#1f2430"
```

## Typography

**Inter** paired with Apple's system font stack (`-apple-system`, `BlinkMacSystemFont`, `SF Pro`) serves as the unified typeface across headlines, body copy, and tabular metrics (`tabular-nums`) for clean, Apple-inspired typographic consistency.

```yaml
typography:
  display-lg:
    fontFamily: "Inter"
    fontSize: 44px
    fontWeight: 700
    lineHeight: 50px
    letterSpacing: -1.2px
  display-lg-mobile:
    fontFamily: "Inter"
    fontSize: 30px
    fontWeight: 700
    lineHeight: 36px
    letterSpacing: -0.8px
  headline-lg:
    fontFamily: "Inter"
    fontSize: 26px
    fontWeight: 600
    lineHeight: 32px
    letterSpacing: -0.5px
  headline-md:
    fontFamily: "Inter"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 28px
    letterSpacing: -0.3px
  title-md:
    fontFamily: "Inter"
    fontSize: 16px
    fontWeight: 600
    lineHeight: 24px
    letterSpacing: -0.2px
  body-lg:
    fontFamily: "Inter"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
    letterSpacing: -0.1px
  body-md:
    fontFamily: "Inter"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
    letterSpacing: 0px
  body-sm:
    fontFamily: "Inter"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 18px
    letterSpacing: 0px
  label-lg:
    fontFamily: "Inter"
    fontSize: 14px
    fontWeight: 500
    lineHeight: 20px
    letterSpacing: -0.1px
  label-md:
    fontFamily: "Inter"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 16px
    letterSpacing: 0px
  label-sm:
    fontFamily: "Inter"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 16px
    letterSpacing: 0.1px
  mono-lg:
    fontFamily: "Inter"
    fontSize: 30px
    fontWeight: 700
    lineHeight: 36px
    letterSpacing: -0.8px
  mono-md:
    fontFamily: "Inter"
    fontSize: 13px
    fontWeight: 500
    lineHeight: 20px
    letterSpacing: 0px
  mono-sm:
    fontFamily: "Inter"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 16px
    letterSpacing: 0px
```

## Layout & Spacing

The application uses a centered **Fixed-Max-Width Grid** (`max-w-7xl`, 1280px) on desktop with generous Apple-style breathing room and a 4px/8px base spacing rhythm. Content adapts cleanly down to tablet (2-column KPIs) and mobile (single-column stacked cards).

```yaml
spacing:
  gutter-xs: 4px
  gutter-sm: 8px
  gutter-md: 16px
  gutter-lg: 24px
  gutter-xl: 32px
  section: 64px
  page-margin: 24px
```

## Elevation & Depth

Hierarchy is established through **pure white cards** (`#ffffff`) over a cool off-white canvas (`#f5f6fa`), subtle **1px hairline borders** (`#e5e7eb`), and ultra-soft ambient elevation shadows (`0 2px 12px rgba(17, 19, 24, 0.02)`). The sticky top navigation uses a frosted-glass backdrop (`backdrop-blur-xl`).

## Shapes

RepoLens pairs generous Apple-style rounded bento cards (`16px–28px`) with fully rounded pill controls (`rounded-full`) for primary obsidian CTAs, segmented tab switchers, search bars, and score badges.

```yaml
rounded:
  sm: 8px
  DEFAULT: 10px
  md: 16px
  lg: 22px
  xl: 28px
  full: 9999px
```

## Motion & Micro-interaction

Transitions are fast, crisp, and understated. Hover states, tab pill slides, and theme toggles settle within 150ms, while the Repo Detail slide-over drawer enters with a smooth deceleration curve.

```yaml
motion:
  state-change:
    duration: 150ms
    delay: 0ms
    timingFunction: ease-out
  surface-enter:
    duration: 240ms
    delay: 0ms
    timingFunction: cubic-bezier(0.2, 0, 0, 1)
  surface-exit:
    duration: 160ms
    delay: 0ms
    timingFunction: cubic-bezier(0.3, 0, 1, 1)
```

## Voice & Content Strategy

- **Tone of voice:** Technical, objective, and helpful. Never judgmental—scores evaluate repository hygiene, not developer skill.
- **Capitalisation:** Sentence case for descriptions and alerts; Title Case for navigation tabs, KPI labels, and primary action buttons (`Analyze Repos`, `Open Evaluator`, `Copy Badge Markdown`).
- **Numbers & Dates:** Always use `tabular-nums` for scores (`78 / 100`), counts (`4,992 / 5,000`), and relative timestamps (`Cached 12m ago`, `Inactive for 28 months`).

## Components

Component implementations live as individual HTML sticker sheet files in
`components/`. Each file contains the canonical, styled HTML components with
`<!-- COMPONENT: name -->` comment markers. Read each file before use.

- **Button** → `@components/button.html`: `button-primary`, `button-icon`, `button-with-icon`
- **Card** → `@components/card.html`: `card-elevated`, `card-action`, `card-interactive`, `card-interactive-2`
- **Modal** → `@components/modal.html`: `modal-standard`, `modal-alert`, `modal-drawer`
- **Searchbar** → `@components/searchbar.html`: `searchbar-standard`, `searchbar-with-suggestions`
- **Table** → `@components/table.html`: `table-standard`, `table-interactive`, `table-selectable`
- **Tabs** → `@components/tabs.html`: `tabs-horizontal`, `tabs-with-icon`
