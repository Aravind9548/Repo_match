import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  images: {
    // GitHub profile pictures shown on the results page.
    remotePatterns: [{ protocol: "https", hostname: "avatars.githubusercontent.com" }],
  },
};

export default nextConfig;
