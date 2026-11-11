function initialize() {
    if (window.lucide) window.lucide.createIcons();
    document.querySelectorAll('[data-panel-selection]').forEach(form => {
        const checkboxes = [...form.querySelectorAll('input[name="panelIds"]')];
        const update = () => {
            const selected = checkboxes.filter(input => input.checked);
            const chunkId = selected[0]?.dataset.chunkId;
            form.querySelector('.selection-count').textContent = `${selected.length} seleccionados`;
            form.querySelector('[data-selection-submit]').disabled = selected.length === 0;
            checkboxes.forEach(input => { input.disabled = !!chunkId && input.dataset.chunkId !== chunkId; });
        };
        form.addEventListener('change', update);
        update();
    });
}
document.addEventListener('DOMContentLoaded', initialize);
document.addEventListener('change', event => {
    const input = event.target;
    if (!input.matches('[data-image-upload] input[type="file"]')) return;
    input.setCustomValidity(input.files[0]?.size > 20000000 ? 'La imagen supera el limite de 20 MB.' : '');
});
document.addEventListener('click', async event => {
    const button = event.target.closest('[data-copy-prompt]');
    if (!button) return;
    const form = button.closest('[data-image-upload]');
    const prompt = form.querySelector('textarea[name="prompt"]').value;
    const characters = form.querySelector('[data-character-context]')?.textContent;
    const status = form.querySelector('[data-copy-status]');
    try {
        await navigator.clipboard.writeText(prompt + (characters ? `\n\n${characters}` : ''));
        status.textContent = 'Prompt copiado';
    } catch {
        status.textContent = 'No se pudo copiar el prompt';
    }
});
document.addEventListener('htmx:afterSettle', initialize);
document.addEventListener('htmx:configRequest', event => { event.detail.headers['X-Requested-With'] = 'XMLHttpRequest'; });
document.addEventListener('htmx:beforeSwap', event => {
    if (event.detail.xhr.status === 400 || event.detail.xhr.status === 409) {
        event.detail.shouldSwap = true;
        event.detail.isError = false;
    }
});