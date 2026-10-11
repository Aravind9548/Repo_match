import type { Metadata } from "next";
import { ProfileForm } from "@/components/apply/ProfileForm";
import { PageHeader } from "@/components/jobs/ui";
import { NextStep } from "@/components/FlowStrip";

export const metadata: Metadata = { title: "Your details · RepoMatch" };

export default function ProfilePage() {
  return (
    <div className="mx-auto max-w-[1100px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Set up once" title="The same ten questions." accent="Answered once.">
        Name, phone, work authorisation, graduation — every form asks them again. Keep your answers here and each apply
        kit comes pre-filled, with the job-specific answers drafted from your resume and repositories.
      </PageHeader>
      <div className="mt-14">
        <ProfileForm />
      </div>

      <NextStep
        href="/tracker"
        label="Back to your applications"
        hint="Every apply kit starts from these answers, so they only need filling in once."
      />
    </div>
  );
}
