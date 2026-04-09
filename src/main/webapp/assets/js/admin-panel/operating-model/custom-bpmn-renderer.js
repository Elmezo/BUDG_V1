/**
 * Custom BPMN Renderer Module
 * Overrides the default UserTask renderer to position the user icon at top-left corner (0, 0)
 */
(function() {
    'use strict';

    var CRS_PREFIX = 'adminPanel.operatingModel.customBpmnRenderer';
    function crsT(key, fallback) {
        if (typeof adminT === 'function') {
            return adminT(CRS_PREFIX + '.' + key, fallback);
        }
        return fallback;
    }

    function CustomBpmnRenderer(config, injector) {

        const bpmnRenderer = injector.get('bpmnRenderer', false);

        if (!bpmnRenderer) {
            console.error(crsT('bpmnRendererNotFound', '[CustomBpmnRenderer] bpmnRenderer not found!'));
            return;
        }

        // Get required services
        const pathMap = injector.get('pathMap');
        const styles = injector.get('styles', false);
        const eventBus = injector.get('eventBus', false);
        const elementRegistry = injector.get('elementRegistry', false);

        function drawUserIcon(parentGfx, element, attrs, pickedAttrs, getFillColor, getStrokeColor, defaultFillColor, defaultStrokeColor) {

            const drawPath = bpmnRenderer._drawPath;
            if (!drawPath) {
                console.error('[drawUserIcon] _drawPath not found!');
                return;
            }

            // Remove old user icon
            const allPaths = parentGfx.querySelectorAll('path');
            allPaths.forEach(function(path) {
                const d = path.getAttribute('d') || '';
                if (
                    d.includes('TASK_TYPE_USER') ||
                    d.includes('0.909,-0.845') ||
                    d.includes('2.162,1.009') ||
                    d.includes('m 15,12') ||
                    d.includes('m 4 4')
                ) {
                    path.remove();
                }
            });

            // Use larger user icon path directly
            const userIconPath = 'm 15,12 c 0.909,-0.845 1.594,-2.049 1.594,-3.385 0,-2.554 -1.805,-4.62199999 -4.357,-4.62199999 -2.55199998,0 -4.28799998,2.06799999 -4.28799998,4.62199999 0,1.348 0.974,2.562 1.89599998,3.405 -0.52899998,0.187 -5.669,2.097 -5.794,4.7560005 v 6.718 h 17 v -6.718 c 0,-2.2980005 -5.5279996,-4.5950005 -6.0509996,-4.7760005 zm -8,6 l 0,5.5 m 11,0 l 0,-5';

            // Draw the user icon with larger size
            // Use white fill and rgb(34, 36, 42) stroke as specified
            drawPath(parentGfx, userIconPath, {
                fill: 'white',
                stroke: 'rgb(34, 36, 42)',
                strokeWidth: 0.5,
                strokeLinecap: 'round',
                strokeLinejoin: 'round'
            });
        }


        // Helper functions for color
        const getFillColor = function(element, defaultColor, overrideColor) {
            if (overrideColor) return overrideColor;
            if (styles && styles.getStyle) {
                const style = styles.getStyle(element, 'fill');
                if (style) return style;
            }
            return defaultColor || '#ffffff';
        };

        const getStrokeColor = function(element, defaultColor, overrideColor) {
            if (overrideColor) return overrideColor;
            if (styles && styles.getStyle) {
                const style = styles.getStyle(element, 'stroke');
                if (style) return style;
            }
            return defaultColor || '#000000';
        };

        const defaultFillColor = '#ffffff';
        const defaultStrokeColor = '#000000';

        // Store original Task handler
        const originalTaskHandler = bpmnRenderer.handlers['bpmn:Task'];

        // Override bpmn:Task handler ONLY
        bpmnRenderer.handlers['bpmn:Task'] = function(parentGfx, element, attrs) {

            // 1) Render base task using original Task handler
            const shape = originalTaskHandler.call(
                bpmnRenderer,
                parentGfx,
                element,
                attrs
            );

            // 2) Draw user icon at top left
            setTimeout(function() {
                const pickedAttrs = {};
                if (attrs?.fill !== undefined) pickedAttrs.fill = attrs.fill;
                if (attrs?.stroke !== undefined) pickedAttrs.stroke = attrs.stroke;

                drawUserIcon(
                    parentGfx,
                    element,
                    attrs,
                    pickedAttrs,
                    getFillColor,
                    getStrokeColor,
                    defaultFillColor,
                    defaultStrokeColor
                );
            }, 0);

            return shape;
        };

        // Listen for shape changes to redraw icon when element is updated
        if (eventBus && elementRegistry) {
            eventBus.on('shape.changed', function(event) {
                const element = event.element;
                const gfx = event.gfx;


                // Only handle bpmn:Task elements
                if (element && element.type === 'bpmn:Task' && gfx) {
                    setTimeout(function() {
                        const pickedAttrs = {};
                        const attrs = {};

                        drawUserIcon(
                            gfx,
                            element,
                            attrs,
                            pickedAttrs,
                            getFillColor,
                            getStrokeColor,
                            defaultFillColor,
                            defaultStrokeColor
                        );
                    }, 0);
                } else {
                }
            });
        } else {
            console.warn(crsT('shapeChangedListenerMissing', '[CustomBpmnRenderer] Cannot register shape.changed listener - missing eventBus or elementRegistry'));
        }

    }

    CustomBpmnRenderer.$inject = ['config', 'injector'];

    // Export as BPMN.js module
    // Using __init__ to run the initialization function directly
    window.CustomBpmnRenderer = {
        __init__: [CustomBpmnRenderer]
    };

})();

