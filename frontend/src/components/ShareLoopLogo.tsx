export function ShareLoopLogo({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`shareloop-logo relative inline-block shrink-0 overflow-hidden bg-white ${className}`}
    >
      <img
        src="/images/shareloop-logo.webp"
        alt=""
        width="256"
        height="256"
        decoding="async"
        className="absolute left-1/2 top-1/2 size-[145%] max-w-none -translate-x-1/2 -translate-y-1/2 object-contain"
      />
    </span>
  );
}
