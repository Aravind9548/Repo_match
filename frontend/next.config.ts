import type { NextConfig } from "next";

const nextConfig: NextConfig = {
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
