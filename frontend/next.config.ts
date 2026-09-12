import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server bundle for our own Docker image; Vercel builds its own way.
  output: process.env.VERCEL ? undefined : "standalone",
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
