function initialize() {
    if (window.lucide) window.lucide.createIcons();
    document.querySelectorAll('[data-panel-selection]').forEach(form => {
        const checkboxes = [...form.querySelectorAll('input[type="checkbox"]')];
        const update = () => {
            const count = checkboxes.filter(input => input.checked).length;
            form.querySelector('.selection-count').textContent = `${count} / 2 seleccionados`;
            form.querySelector('[data-selection-submit]').disabled = count < 1 || count > 2;
            checkboxes.forEach(input => { input.disabled = count >= 2 && !input.checked; });
        };
        form.addEventListener('change', update);
        update();
    });
}
document.addEventListener('DOMContentLoaded', initialize);
document.addEventListener('htmx:afterSettle', initialize);
document.addEventListener('htmx:configRequest', event => { event.detail.headers['X-Requested-With'] = 'XMLHttpRequest'; });
document.addEventListener('htmx:beforeSwap', event => {
    if (event.detail.xhr.status === 400 || event.detail.xhr.status === 409) {
        event.detail.shouldSwap = true;
        event.detail.isError = false;
    }
});