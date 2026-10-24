export function ShareLoopLogo({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`shareloop-logo relative inline-block shrink-0 overflow-hidden bg-white ${className}`}
    >
      <img
        src="/images/shareloop-logo.png"
        alt=""
        width="1254"
        height="1254"
        className="absolute left-1/2 top-1/2 size-[145%] max-w-none -translate-x-1/2 -translate-y-1/2 object-contain"
      />
    </span>
  );
}
