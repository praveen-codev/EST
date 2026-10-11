import { Suspense } from "react";
import { SignIn } from "@/components/auth/sign-in";

export default function SignInPage() {
  return (
    <Suspense>
      <SignIn />
    </Suspense>
  );
}
