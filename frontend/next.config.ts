import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server bundle for the Docker image (Cloud Run).
  output: "standalone",
  images: {
    remotePatterns: [
      // GitHub profile pictures
      { protocol: "https", hostname: "avatars.githubusercontent.com" },
      // GitHub's generated social-preview cards for repositories
      { protocol: "https", hostname: "opengraph.githubassets.com" },
    ],
  },
};

export default nextConfig;
