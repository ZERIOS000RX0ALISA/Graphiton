# Graphiton

A smartphone-based remote control interface for local AI image generation
and image editing.

## Overview

Graphiton connects a smartphone to a computer running a compatible
image-generation server.

Instead of performing image generation directly on the phone, the
application provides a mobile interface for sending requests to the
computer and managing supported image-generation operations.

The project is intended to make local image-generation workflows more
accessible from a mobile device.

## Features

### Remote Image Generation

Graphiton is designed to communicate with a local image-generation server
running on a computer.

Supported workflows include:

- **Text-to-Image (T2I):** Generate images from text prompts.
- **Image-to-Image (Img2Img):** Submit an existing image for
  AI-assisted transformation.

These operations require a compatible server and the appropriate
generation models on the computer.

### Image Editing

The application includes image-editing controls such as:

- Cropping.
- Rotation.
- Shading adjustments.

The availability and behavior of individual editing controls depend on
the current application implementation.

### Generation Settings

Graphiton provides controls for configuring supported generation
parameters, including:

- Image quality or resolution.
- Number of iterations or steps per image.
- Request timeout.

The exact meaning and available values of these settings depend on the
connected backend and the application's implementation.

### Mobile-to-Computer Workflow

The smartphone acts as the control interface, while the connected
computer performs the image-generation workload.

This allows users to access their computer's image-generation environment
without requiring the generation model to run on the phone itself.

## Architecture

The intended workflow is:

1. A compatible image-generation server runs on the computer.
2. The server exposes a network endpoint.
3. The smartphone connects to the server.
4. The user configures an image-generation request.
5. The application sends the request to the server.
6. The computer processes the request and returns the result.

Actual connection requirements depend on the server configuration.

## Requirements

- A compatible Android smartphone.
- A computer capable of running the selected image-generation backend.
- A compatible image-generation server.
- Network connectivity between the smartphone and computer.
- Any models, dependencies, and supporting software required by the
  selected backend.

The exact server setup and supported versions should be documented after
verifying the current project configuration.

## Project Status

**Status: Functional prototype undergoing refinement**

The application has been tested on a physical smartphone, and its main
remote image-generation workflow is operational.

Remaining work includes interface refinement, optimization, and the
addition or verification of publisher, contact, and version information.

## Security Considerations

When connecting a smartphone to a local server:

- Use trusted networks.
- Avoid exposing an unauthenticated server directly to the public internet.
- Do not commit API keys, passwords, private addresses, or other secrets
  to the repository.
- Review server authentication and access-control settings before
  allowing remote connections.
- Treat externally accessible image-generation endpoints as services
  that require appropriate security controls.

## Technology

Graphiton was developed with assistance from Google AI Studio.

The specific frameworks, programming languages, networking libraries,
and server API compatibility should be documented after inspecting the
project source.

## Open Source

The repository is intended to support inspection, experimentation,
modification, and community contributions.

Contributions, bug reports, and suggestions are welcome once the
repository's contribution guidelines are established.

## Disclaimer

Graphiton is a client interface for supported image-generation workflows.
It does not itself guarantee the availability, performance, or quality
of the connected server or its models.

## License

The project license will be specified in the repository's `LICENSE` file.
Until a license is added, do not assume that the repository grants
permission to redistribute or modify its contents.
