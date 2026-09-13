/**
 * Re-mounts on every navigation (unlike the layout), so each page enters with the same short fade-and-lift.
 * The header and footer live in the layout and stay put, which is what makes the change read as one app.
 */
export default function Template({ children }: { children: React.ReactNode }) {
  return <div className="page-enter">{children}</div>;
}
