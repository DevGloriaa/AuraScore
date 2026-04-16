import { NextResponse } from "next/server";

export async function POST(request: Request) {
  try {
    const rawBody = await request.text();
    const forwardedHeaders = new Headers(request.headers);

    forwardedHeaders.delete("host");
    forwardedHeaders.delete("origin");
    forwardedHeaders.delete("referer");

    const upstreamResponse = await fetch("https://qa.interswitchng.com/passport/oauth/token", {
      method: "POST",
      headers: forwardedHeaders,
      body: rawBody,
    });

    const data = await upstreamResponse.json();

    return NextResponse.json(data, {
      status: upstreamResponse.status,
    });
  } catch (error) {
    console.error("Interswitch proxy request failed:", error);

    return NextResponse.json(
      { error: "Bad Gateway" },
      { status: 502 }
    );
  }
}