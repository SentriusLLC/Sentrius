# Version Management Changes

## Overview

The version tracking system for Docker images has been simplified for local development. This document explains the changes and how to use the new system.

## What Changed

### Before
- `.local.env` file tracked version numbers for all Docker images
- Every local build incremented the version number
- Version numbers were stored and tracked in version control

### After
- Local builds now always use `:latest` tag
- No version tracking needed for local development
- `.local.env` is deprecated for local builds (kept for backward compatibility)
- `.gcp.env` still tracks versions for GCP deployments

## Benefits

1. **Simplified Development**: No need to track version numbers during local development
2. **Cleaner Git History**: No more version number updates cluttering commits
3. **Easier Collaboration**: No conflicts from version number changes
4. **Consistent with Docker Best Practices**: Using `:latest` for local dev is standard

## Usage

### Building Images Locally

```bash
# Build a single image (always uses :latest)
./ops-scripts/base/build-images.sh local --sentrius

# Build all images (always uses :latest)
./ops-scripts/base/build-images.sh local --all
```

### Deploying Locally

```bash
# Deploy to local Kubernetes (uses :latest tags)
./ops-scripts/local/deploy-helm.sh
```

### Building for GCP

```bash
# Build for GCP (still uses version tracking from .gcp.env)
./ops-scripts/base/build-images.sh gcp --sentrius

# Versions are automatically incremented and stored in .gcp.env
```

## Migration Guide

If you have existing local deployments:

1. **No action required** - The changes are backward compatible
2. Existing images with version tags will continue to work
3. New builds will create `:latest` tags alongside any existing versioned images
4. Next time you build, the `:latest` tag will be used

## File Reference

- `.env` - Legacy reference file (deprecated)
- `.local.env` - Local version tracking (deprecated, kept for compatibility)
- `.gcp.env` - GCP version tracking (still active and required for GCP deployments)
- `.env.bak`, `.local.env.bak`, `.gcp.env.bak` - Backup files (gitignored)

## Technical Details

### Build Script Changes

The `build-images.sh` script now:
1. Only loads `.env` file when building for GCP
2. Sets version to "latest" for local builds before calling `build_image()`
3. Skips version increment and env file updates for local builds

### Deploy Script Changes

The `deploy-helm.sh` script now:
1. Sets all version variables to "latest" at startup
2. Does not source `.local.env`
3. Passes "latest" to all Helm `--set` commands for image tags

## Troubleshooting

### Images not found

If you get "image not found" errors:
```bash
# Rebuild the images
./ops-scripts/base/build-images.sh local --all
```

### Want to use specific versions locally

If you need specific versions for local testing, you can override via environment variables:
```bash
# Override a single service version
SENTRIUS_VERSION="1.2.3" ./ops-scripts/local/deploy-helm.sh

# Override multiple service versions
SENTRIUS_VERSION="1.2.3" SENTRIUS_SSH_VERSION="1.1.5" ./ops-scripts/local/deploy-helm.sh
```

Or pass it directly to helm:
```bash
helm upgrade --install sentrius ./sentrius-chart \
  --set sentrius.image.tag=1.2.3 \
  ...
```
