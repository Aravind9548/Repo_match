"use client";

import { useEffect, useState } from "react";
import { newTrackerKey } from "@/lib/api";

const STORAGE = "repomatch.trackerKey";

function read(): string | null {
  try {
    return localStorage.getItem(STORAGE);
  } catch {
    return null;
  }
}

function write(key: string) {
  try {
    localStorage.setItem(STORAGE, key);
  } catch {
    // Private mode: the key lives for this page only.
  }
}

/**
 * The tracker has no accounts: alerts and applications belong to a random key kept in this browser.
 * A `?key=` in the URL (from an alert email) wins and is remembered, so email links work on any device.
 */
export function useTrackerKey({ create = false }: { create?: boolean } = {}) {
  const [key, setKey] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const fromUrl = new URLSearchParams(window.location.search).get("key");
      let k = fromUrl || read();
      if (fromUrl) {
        write(fromUrl);
        // Keep the secret out of the address bar, history and screenshots.
        const url = new URL(window.location.href);
        url.searchParams.delete("key");
        window.history.replaceState(null, "", url);
      }
      if (!k && create) {
        try {
          k = (await newTrackerKey()).key;
          write(k);
        } catch {
          k = null;
        }
      }
      if (!cancelled) {
        setKey(k);
        setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [create]);

  function restore(value: string) {
    const v = value.trim();
    if (!v) return;
    write(v);
    setKey(v);
  }

  return { key, ready, restore };
}
