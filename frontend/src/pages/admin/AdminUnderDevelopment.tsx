import { PageHeader } from '../../components/ui';

export function AdminUnderDevelopment({ title }: { title: string }) {
  return (
    <section>
      <PageHeader title={title} description="Chức năng đang phát triển." />
    </section>
  );
}
